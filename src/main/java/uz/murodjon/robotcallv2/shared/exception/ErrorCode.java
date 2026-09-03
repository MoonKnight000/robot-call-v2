package uz.murodjon.robotcallv2.shared.exception;

/**
 * Every message the API can return, paired with its code in one place — a frontend
 * translates by {@link #name()} (sent as {@code ResponseData.messageCode}), so a message's
 * wording is free to change here without ever breaking that mapping, and a code can never
 * exist without a message or vice versa.
 */
public enum ErrorCode {

    // not found
    CAMPAIGN_NOT_FOUND("Campaign %s not found"),
    TARGET_NOT_FOUND("Target %s not found"),
    COMPANY_NOT_FOUND("Company %s not found"),
    COMPANY_CONFIG_NOT_FOUND("Company config %s not found"),
    CONTACT_NOT_FOUND("Contact %s not found"),
    CRM_INTEGRATION_NOT_FOUND("CRM integration %s not found"),
    INBOUND_ROUTE_NOT_FOUND("Inbound route %s not found"),
    REPORT_SCHEDULE_NOT_FOUND("Report schedule %s not found"),
    SCENARIO_NOT_FOUND("Scenario %s not found"),
    SIP_TRUNK_NOT_FOUND("SIP trunk %s not found"),
    FILE_NOT_FOUND("File %s not found"),
    USER_NOT_FOUND("User %s not found"),
    CALL_NOT_FOUND("Call %s not found"),
    DO_NOT_CALL_ENTRY_NOT_FOUND("No active do-not-call entry for %s"),
    OPERATOR_SNAPSHOT_NOT_FOUND("No operator snapshot for channel %s"),
    CALL_RECORDING_NOT_FOUND("No recording for call %s"),

    // forbidden
    NO_USER_SESSION("no user session on this request"),
    ACCOUNT_BLOCKED("this account has been blocked"),
    ACCOUNT_NOT_ACTIVATED("account not activated yet — use the invite link first"),
    ACCOUNT_INACTIVE("this account is no longer active"),
    REFRESH_TOKEN_INVALID("invalid or already used refresh token"),
    REFRESH_TOKEN_EXPIRED("refresh token has expired — log in again"),
    SCENARIO_BUILTIN_READONLY("Built-in scenario '%s' cannot be edited — clone it first"),

    // validation
    LOGIN_INVALID_CREDENTIALS("username yoki parol noto'g'ri"),
    ACTIVATION_TOKEN_INVALID("invalid or already used activation token"),
    ACTIVATION_TOKEN_EXPIRED("activation token has expired — ask an admin to invite you again"),
    RESET_TOKEN_INVALID("invalid or already used reset token"),
    RESET_TOKEN_EXPIRED("reset token has expired — request a new one"),
    SCENARIO_DEFINITION_INVALID("Invalid scenario definition: %s"),
    CAMPAIGN_DIAL_WINDOW_INVALID("dialWindowStart must be before dialWindowEnd"),
    CAMPAIGN_DIAL_WINDOW_OUT_OF_RANGE(
            "Campaign dial window (%s-%s) must fit inside the company's allowed dial window (%s-%s)"),
    CAMPAIGN_RECURRENCE_INVALID("Invalid campaign recurrence configuration: %s"),
    TTS_VOICE_UNKNOWN("Unknown TTS voice '%s'; available: %s"),
    TTS_VOICE_LANGUAGE_MISMATCH("TTS voice '%s' speaks %s, so it cannot be the voice for %s"),
    ENGINE_STT_PROVIDER_UNKNOWN("Unknown STT provider '%s'; available: %s"),
    ENGINE_TTS_PROVIDER_UNKNOWN("Unknown TTS provider '%s'; available: %s"),
    ENGINE_REALTIME_NOT_AVAILABLE("REALTIME mode has no engine wired in this build — use CASCADE"),
    ENGINE_REALTIME_PROVIDER_UNKNOWN("Unknown realtime engine '%s'; available: %s"),
    ENGINE_REALTIME_PROVIDER_REQUIRED("realtimeProvider is required — no default is configured; pick one of %s"),
    CSV_EMPTY("CSV is empty"),
    CSV_PHONE_COLUMN_MISSING("CSV needs a 'phone' column; found: %s"),
    CSV_PHONE_EMPTY("phone is empty"),
    CSV_NAME_EMPTY("name is empty"),
    CSV_CLIENT_ID_NOT_NUMBER("clientId '%s' is not a number"),
    COMPANY_CONFIG_DEFAULT_LANGUAGE_NOT_IN_SUPPORTED("defaultLanguage '%s' must be one of supportedLanguages %s"),
    LANGUAGE_CODE_INVALID("%s"),
    COMPANY_CONFIG_LANGUAGE_NOT_SUPPORTED("Language '%s' is not supported by this company; supported: %s"),
    COMPANY_CONFIG_DISCLOSURE_INCOMPLETE(
            "disclosureText qo'ng'iroq avtomatik ekanini va yozib olinayotganini aytishi shart (§11.1); "
                    + "platforma matni ishlatilishi uchun bo'sh qoldiring"),
    EMAIL_ALREADY_TAKEN("bu email allaqachon band"),
    CURRENT_PASSWORD_INCORRECT("joriy parol noto'g'ri"),
    SCHEDULE_START_AFTER_END("boshlanish vaqti tugash vaqtidan oldin bo'lishi kerak"),
    PROFILE_TABLE_CONFIG_KEY_BLANK("config key bo'sh bo'lishi mumkin emas"),
    PROFILE_TABLE_CONFIG_KEY_TOO_LONG("config key 100 belgidan oshmasligi kerak"),
    SUPERADMIN_GRANT_FORBIDDEN("SUPERADMIN cannot be granted through company user management"),
    SEARCH_QUERY_BLANK("q: must not be blank"),
    REPORT_BULK_ACTION_UNKNOWN("Unknown bulk action '%s'"),
    REPORT_SCHEDULE_FORMAT_INVALID("format must be one of %s, got '%s'"),
    REPORT_EXPORT_FORMAT_UNKNOWN("Unknown export format '%s' — use csv, pdf, or xlsx"),
    SCENARIO_KEY_DERIVE_FAILED("Could not derive a scenario key from name '%s'"),
    CRM_INTEGRATION_APP_NOT_CONFIGURED("Save appName/grants first"),
    OAUTH_STATE_INVALID("invalid state"),
    DATE_RANGE_INVALID("'from' must be before 'to'"),
    DATE_RANGE_TOO_LONG("range must not exceed %s days"),
    INSTANT_PARSE_FAILED("'%s' is not an ISO-8601 instant"),
    IMAGE_UPLOAD_FILE_MISSING("no file uploaded"),
    IMAGE_UPLOAD_TOO_LARGE("image must be at most 5 MB"),
    IMAGE_UPLOAD_TYPE_UNSUPPORTED("unsupported image type '%s'; allowed: %s"),
    IMAGE_UPLOAD_READ_FAILED("could not read uploaded file: %s"),
    PHONE_INVALID("Invalid phone number: '%s' (expected 3-15 digits, optional leading '+')"),
    SIP_TRUNK_PASSWORD_REQUIRED("sipPassword is required for a managed trunk"),
    SIP_TRUNK_PASSWORD_REQUIRED_ON_SWITCH("sipPassword is required when switching a trunk to managed mode"),
    SIP_TRUNK_MODE_CONFLICT(
            "provide either pjsipEndpoint (manual mode) or host/sipUsername/sipPassword (managed mode), not both"),
    SIP_TRUNK_MODE_MISSING(
            "either pjsipEndpoint (manual mode) or host+sipUsername+sipPassword (managed mode) is required"),
    SIP_TRUNK_USERNAME_REQUIRED("sipUsername is required for a managed trunk"),
    SIP_TRUNK_TRANSPORT_UNSUPPORTED("transport '%s' is not wired to an Asterisk transport yet — only UDP is supported"),
    PLAYBACK_FILE_BLANK("file must not be blank"),
    PLAYBACK_FILE_OUTSIDE_BASE("file must be inside %s"),
    PLAYBACK_FILE_NOT_FOUND("No such playback file: %s"),
    SAY_TEXT_BLANK("text must not be blank"),
    SAY_TEXT_TOO_LONG("text is longer than %s characters"),

    // conflict
    CONTACT_PHONE_EXISTS("Contact with phone %s already exists"),
    TTS_PROVIDER_UNAVAILABLE("No TTS provider available for language %s"),
    INBOUND_ROUTE_DID_EXISTS("An enabled inbound route for %s already exists"),
    USER_EMAIL_TAKEN("email %s already registered"),
    USER_USERNAME_TAKEN("username %s already registered"),
    LAST_ADMIN_ROLE_CHANGE_FORBIDDEN("cannot change the role of the last admin — promote another user first"),
    SELF_ACTION_FORBIDDEN("cannot %s your own account"),
    LAST_ADMIN_ACTION_FORBIDDEN("cannot %s the last admin"),
    SCENARIO_KEY_EXISTS("Scenario key '%s' already exists"),
    SIP_TRUNK_DEFAULT_DELETE_FORBIDDEN("Cannot delete the default trunk — set another one as default first"),
    SIP_TRUNK_SELECTION_UNAVAILABLE("None of the selected SIP trunks %s is enabled"),
    CALL_NOT_ACTIVE("No active call for channel %s"),
    TTS_DISABLED("TTS is disabled (voice-agent.tts.enabled=false)"),

    // external service
    ARI_ORIGINATE_FAILED("Originate to %s via %s failed: %s"),
    ARI_PLAYBACK_FAILED("Failed to play %s: %s"),
    ARI_NOT_CONNECTED("ARI is not connected"),
    STT_GOOGLE_CLIENT_UNAVAILABLE("client is not available"),
    STT_YANDEX_CHANNEL_UNAVAILABLE("channel is not available (check STT_YANDEX_API_KEY)"),
    STT_DEEPGRAM_CONNECT_FAILED("deepgram connection failed: %s"),
    STT_DEEPGRAM_STREAM_FAILED("deepgram stream failed: %s"),
    TTS_GOOGLE_CLIENT_UNAVAILABLE("client is not available"),
    TTS_GOOGLE_AUDIO_PARSE_FAILED("failed to parse audio: %s"),
    TTS_YANDEX_CHANNEL_UNAVAILABLE("channel is not available (check TTS_YANDEX_API_KEY)"),
    TTS_YANDEX_STREAM_ERROR("stream failed: %s"),
    STT_AISHA_CONNECT_FAILED("realtime connection failed: %s"),
    TTS_AISHA_CONNECT_FAILED("realtime connection failed: %s"),
    TTS_AISHA_SYNTH_FAILED("synthesis failed: %s"),
    TTS_AISHA_AUDIO_PARSE_FAILED("failed to parse audio: %s"),
    REALTIME_GEMINI_CONNECT_FAILED("realtime connection failed: %s"),
    REALTIME_GEMINI_SETUP_FAILED("session setup was not acknowledged within %ss"),
    UYSOT_OAUTH_LOGIN_NOT_AVAILABLE("not configured yet (ROADMAP Bosqich D)"),
    UYSOT_OAUTH_NOT_CONFIGURED("Uysot OAuth endpoints are not configured yet"),
    UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR("token exchange HTTP %s"),
    UYSOT_OAUTH_TOKEN_EXCHANGE_FAILED("token exchange failed: %s"),
    UYSOT_OAUTH_TOKEN_MISSING("token response had no access_token"),
    ENCRYPTION_KEY_NOT_SET("voice-agent.encryption.secret-key is not set"),
    REPORT_PDF_RENDER_FAILED("Failed to render report PDF: %s"),
    REPORT_XLSX_RENDER_FAILED("Failed to render report XLSX: %s"),
    IMAGE_UPLOAD_STORAGE_UNAVAILABLE("image upload failed — storage unavailable"),
    FILE_READ_FAILED("could not read file %s"),

    // framework-level (ApiExceptionHandler) — not an AppException, but the same envelope
    VALIDATION_FAILED("Validation failed"),
    MALFORMED_REQUEST_BODY("Malformed or invalid request body"),
    MISSING_REQUIRED_PARAMETER("Missing required parameter: %s"),
    INVALID_PARAMETER_VALUE("Invalid value for parameter: %s"),
    NO_SUCH_ENDPOINT("No such endpoint: %s"),
    METHOD_NOT_ALLOWED("Request method '%s' is not supported"),
    INTERNAL_SERVER_ERROR("Internal server error");

    private final String template;

    ErrorCode(String template) {
        this.template = template;
    }

    /** Interpolates {@code args} into this code's template with {@link String#format}. */
    public String format(Object... args) {
        return args.length == 0 ? template : String.format(template, args);
    }
}
