package at.softwarecraftsmen.docker

class ImageName implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String userAndRepository
    public final String tag

    ImageName(String name) {
        Objects.requireNonNull(name, 'name is required')

        int tagIdx = name.lastIndexOf(':');
        if (tagIdx != -1) {
            this.userAndRepository = name.substring(0, tagIdx);
            if (tagIdx < name.length() - 1) {
                this.tag = name.substring(tagIdx + 1);
            } else {
                this.tag = "latest";
            }
        } else {
            this.userAndRepository = name;
            this.tag = "latest";
        }
    }

    ImageName(String userAndRepository, String tag) {
        Objects.requireNonNull(userAndRepository, 'userAndRepository is required')
        this.userAndRepository=userAndRepository
        this.tag=tag
    }

    String toString() {
        "${userAndRepository}:${tag}"
    }

    ImageName withRegistry(String registry) {
        return new ImageName("${registry}${toString()}")
    }
    ImageName withTag(String tag) {
        new ImageName(userAndRepository, tag)
    }
}
