package com.runeliteplugins.reporting;

/** Per-plugin identity and trusted transport configuration; never contains secrets. */
public final class ReportingSettings
{
    private final String displayName;
    private final String draftDirectory;
    private final String endpoint;

    public ReportingSettings(String displayName, String draftDirectory, String endpoint)
    {
        // A single directory component prevents one plugin from opening another's drafts.
        if (displayName == null || displayName.isBlank() || displayName.length() > 40
            || draftDirectory == null || draftDirectory.isBlank()
            || draftDirectory.contains("/") || draftDirectory.contains("\\")
            || draftDirectory.equals(".") || draftDirectory.equals("..") || endpoint == null)
        {
            throw new IllegalArgumentException("Invalid reporting configuration.");
        }
        this.displayName = displayName;
        this.draftDirectory = draftDirectory;
        this.endpoint = endpoint;
    }

    public String getDisplayName() { return displayName; }
    public String getDraftDirectory() { return draftDirectory; }
    public String getEndpoint() { return endpoint; }
}
