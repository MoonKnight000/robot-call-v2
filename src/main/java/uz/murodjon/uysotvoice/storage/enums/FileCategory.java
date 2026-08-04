package uz.murodjon.uysotvoice.storage.enums;

/** {@code stored_file.category} — also the MinIO object-key folder under {@code com-{companyId}/}. */
public enum FileCategory {
    IMAGE("images"),
    AUDIO("audio"),
    DOCUMENT("documents");

    private final String folder;

    FileCategory(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}
