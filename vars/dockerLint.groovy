// TODO: move to some Docker shared library
def call(def dockerfilePath='Dockerfile') {
    sh "docker run --rm -i lukasmartinelli/hadolint < ${dockerfilePath}"
}
