/**
 * <pre>
 *  <code>
 *  infrastructurePipeline {
 *      project = 'jenkins'
 *      artifact = [ 'master', 'backup']
 *      credentialsId = 'dockerRegistryDeployer'
 *  }
 *  </code>
 * </pre>
 */

def call(Closure body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.delegate = config
    body()

    config.project
    config.artifact
    config.dockerRegistryCredentialsId


    node('docker') {
        String project = 'jenkins'
        Set artifact = ['master', 'backup']

        stage('Prepare') {
            checkout scm
        }

        stage ('Build') {
            runBuildStage(config.project, config.artifact)
        }

        stage ('Delivery') {
            runDeliveryStage(config.project, config.artifact, config.dockerRegistryCredentialsId)
        }
    }
}


Map<String,String> vcsMetadata() {
    def metadata=[:]

    metadata.version=sh (script: 'git rev-parse HEAD', returnStdout: true).trim()
    metadata.vcs_ref=sh (script: 'git describe --tags', returnStdout: true).trim()
    metadata.vcs_url=sh (script: 'git config --get remote.origin.url', returnStdout: true).trim()
    metadata.vcs_branch=sh (script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
    metadata.build_date=sh (script: 'date -u +"%Y-%m-%dT%H:%M:%SZ"', returnStdout: true).trim()
    return metadata
}

String buildArguments(Map arguments) {
    def args=""
    for (a in arguments) {
        args <<= " --build-arg ${a.key}=${a.value}"
    }
    args
}

def buildImage(String arguments, String image) {
    sh "docker build ${arguments} -t ${image} ."
}

def tagImage(String sourceImage, String targetImage) {
    sh(script: "docker tag ${sourceImage} ${targetImage}")
}

def pushImage(String image) {
    sh "docker push ${image}"
}

def removeImage(String image) {
    sh "docker image rm ${image}"
}

def runBuildStage(String project, Set artifacts) {
    for (String artifact in artifacts) {
        echo "-------------------------------"
        echo "Building ${artifact}"
        runBuildStage(project, artifact)
    }
}

def runBuildStage(String project, String artifact) {
    dir (artifact) {
        String buildArgs=buildArguments(vcsMetadata())
        buildImage (buildArgs, buildImageName(project, artifact))
    }
}

def runDeliveryStage(String project, Set artifacts, String credentialsId) {
    for (String artifact in artifacts) {
        echo "-------------------------------"
        echo "Building ${artifact}"
        runDeliveryStage(project, artifact, credentialsId)
    }
}

def runDeliveryStage (String project, String artifact, String credentialsId) {
    def imageName=buildImageName(project, artifact)

    try {
        withDockerRegistry([credentialsId: credentialsId, url: "http://${env.DOCKER_REGISTRY}"]) {
            def registryImageName="${env.DOCKER_REGISTRY}${imageName}"
            tagImage imageName, registryImageName
            try {
                pushImage registryImageName
            }
            finally {
                removeImage registryImageName
            }
        }
    }
    finally {
        removeImage imageName
    }
}

String buildImageName(String project, String artifact) {
    return buildImageName("${project}-${artifact}")
}

String buildImageName(String name) {
    def tag = env.BUILD_NUMBER
    return "${name}:${tag}"
}
