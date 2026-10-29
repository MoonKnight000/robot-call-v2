package uz.murodjon.robotcallv2.agent.dialog;

/**
 * Recognized caller intent categories (RETELL_AI_GAP_ANALYSIS.md #6).
 */
public enum Intent {
    INQUIRY,          // General question about product/service
    COMPLAINT,        // Dissatisfaction or problem report
    ORDER,            // Placing or modifying an order
    CANCELLATION,     // Canceling service, appointment, or order
    PAYMENT,          // Payment-related questions or issues
    SUPPORT,          // Technical support or help request
    TRANSFER_REQUEST, // Explicitly asking to speak to a human
    CHAT,             // Casual conversation, no specific goal
    UNKNOWN           // Could not determine intent
}
