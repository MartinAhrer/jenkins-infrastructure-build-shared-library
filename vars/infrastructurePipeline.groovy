


import at.softwarecraftsmen.docker.ImageName

import static Objects.requireNonNull

/**
 * Usage example:
 * <pre>
 *  <code>
 *  infrastructurePipeline {
 *      project = 'jenkins'
 *      artifact = [ 'master', 'backup']
 *      credentialsId = 'dockerRegistryDeployer'
 *  }
 *
 *  </code>
 * </pre>
 */
def call(Closure body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def label=config.label ?: 'docker'

    String tag = env.BUILD_NUMBER
    String deliveryTag=null

    String project = config.project
    def artifact = config.artifact

    node(label) {
        stage('Setup') {
            checkout scm
            try {
                def gitTag=sh(script: 'git describe --tags --exact-match', returnStdout: true).trim()
                deliveryTag = (gitTag =~ /^v?([0-9.\-]+)/)[0][1]
            } catch (Exception e){
            }
        }

        stage('Build') {
            if (artifact instanceof Iterable) {
                for (String a in artifact) {
                    ImageName imageName = artifact ? new ImageName("${project}-${a}", tag) : new ImageName(project, tag)
                    runBuildStage(imageName, a)
                }
            } else {
                ImageName imageName = artifact ? new ImageName("${project}-${artifact}", tag) : new ImageName(project, tag)
                String directory = artifact ?: './'
                runBuildStage(imageName, directory)
            }
        }

        stage('Delivery') {
            if (deliveryTag && config.dockerRegistry?.url) {
                if (artifact instanceof Iterable) {
                    for (String a in artifact) {
                        ImageName imageName = artifact ? new ImageName("${project}-${a}", tag) : new ImageName(project, tag)
                        runDeliveryStage(config.dockerRegistry, imageName, deliveryTag)
                    }
                } else {
                    ImageName imageName = artifact ? new ImageName("${project}-${artifact}", tag) : new ImageName(project, tag)
                    runDeliveryStage(config.dockerRegistry, imageName, deliveryTag)
                }
            }
        }

        stage ('Teardown') {
            if (artifact instanceof Iterable) {
                for (String a in artifact) {
                    ImageName imageName = artifact ? new ImageName("${project}-${a}", tag) : new ImageName(project, tag)
                    removeImage(imageName as String)
                }
            } else {
                ImageName imageName = artifact ? new ImageName("${project}-${artifact}", tag) : new ImageName(project, tag)
                removeImage(imageName as String)
            }
        }
    }
}

Map<String, String> vcsMetadata() {
    def metadata = [:]

    metadata.version = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    metadata.vcs_ref = sh(script: 'git describe --tags', returnStdout: true).trim()
    metadata.vcs_url = sh(script: 'git config --get remote.origin.url', returnStdout: true).trim()
    metadata.vcs_branch = sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
    metadata.build_date = sh(script: 'date -u +"%Y-%m-%dT%H:%M:%SZ"', returnStdout: true).trim()
    return metadata
}

String buildArguments(Map arguments) {
    def args = ""
    for (a in arguments) {
        args <<= " --build-arg ${a.key}=${a.value}"
    }
    args
}

void buildImage(String arguments, String image) {
    echo "Building ${image}"
    sh "docker build ${arguments} -t ${image} ."
}

void tagImage(String sourceImage, String targetImage) {
    sh(script: "docker tag ${sourceImage} ${targetImage}")
}

void pushImage(String image) {
    sh "docker push ${image}"
}

void removeImage(String image) {
    sh "docker image rm ${image}"
}

def runBuildStage(ImageName imageName, String directory) {
    requireImageName(imageName)
    requireNonNull(directory, 'directory is required')

    String buildArgs = buildArguments(vcsMetadata())
    dir(directory) {
        try {
            dockerLint()
        }
        catch (Exception) {
            // Do not fail the build just because of lint check violations
            currentBuild.result = 'UNSTABLE'
        }
        buildImage(buildArgs, imageName as String)
    }
}

private requireImageName(ImageName imageName) {
    requireNonNull(imageName, 'imageName is required')
}

def runDeliveryStage(Map registry, ImageName imageName, String deliveryTag) {
    requireImageName(imageName)
    requireNonNull(registry.url, 'url is required')
    requireNonNull(registry.credentialsId, 'credentialsId is required')
    requireNonNull(deliveryTag, 'deliveryTag is required')

    withDockerRegistry([credentialsId: registry.credentialsId, url: registry.url]) {
        def registryImageName = imageName.
                withRegistry(env.DOCKER_REGISTRY).
                withTag(deliveryTag) as String

        tagImage imageName as String, registryImageName
        try {
            pushImage registryImageName
        }
        finally {
            removeImage registryImageName
        }
    }
}

