package uz.murodjon.robotcallv2.billing.domain.enums;

/**
 * The errors a merchant answers the Payme Merchant API with, and the text Payme shows the
 * payer for each — in Russian, Uzbek and English, because Payme picks the language itself.
 *
 * <p>The codes are Payme's, not ours: changing one breaks the protocol. The texts are free.
 */
public enum PaymeError {

    INVALID_AMOUNT(-31001,
            "Неверная сумма",
            "Noto'g'ri summa",
            "Invalid amount"),
    TRANSACTION_NOT_FOUND(-31003,
            "Транзакция не найдена",
            "Tranzaksiya topilmadi",
            "Transaction not found"),
    CANNOT_CANCEL(-31007,
            "Невозможно отменить: платёж уже зачислен на баланс",
            "Bekor qilib bo'lmaydi: to'lov balansga o'tkazilgan",
            "Cannot cancel: the payment has already been credited"),
    CANNOT_PERFORM(-31008,
            "Невозможно выполнить операцию",
            "Amalni bajarib bo'lmaydi",
            "Unable to perform the operation"),
    TOPUP_NOT_FOUND(-31050,
            "Платёж не найден",
            "To'lov topilmadi",
            "Top-up not found"),
    TOPUP_NOT_PAYABLE(-31051,
            "Платёж уже оплачен, отменён или просрочен",
            "To'lov allaqachon to'langan, bekor qilingan yoki muddati o'tgan",
            "The top-up is already paid, cancelled or expired"),
    TOPUP_BUSY(-31052,
            "Платёж уже оплачивается другой транзакцией",
            "To'lov boshqa tranzaksiya orqali to'lanmoqda",
            "The top-up is being paid by another transaction"),
    SYSTEM_ERROR(-32400,
            "Системная ошибка",
            "Tizim xatosi",
            "System error"),
    INSUFFICIENT_PRIVILEGE(-32504,
            "Недостаточно привилегий для выполнения метода",
            "Metodni bajarish uchun huquq yetarli emas",
            "Insufficient privileges to perform this method"),
    INVALID_REQUEST(-32600,
            "Неверный запрос: нет обязательных полей",
            "Noto'g'ri so'rov: majburiy maydonlar yo'q",
            "Invalid request: required fields are missing"),
    METHOD_NOT_FOUND(-32601,
            "Метод не найден",
            "Metod topilmadi",
            "Method not found"),
    PARSE_ERROR(-32700,
            "Ошибка разбора JSON",
            "JSON'ni o'qib bo'lmadi",
            "Parse error");

    private final int code;
    private final String ru;
    private final String uz;
    private final String en;

    PaymeError(int code, String ru, String uz, String en) {
        this.code = code;
        this.ru = ru;
        this.uz = uz;
        this.en = en;
    }

    public int code() {
        return code;
    }

    public String ru() {
        return ru;
    }

    public String uz() {
        return uz;
    }

    public String en() {
        return en;
    }

    /**
     * Payme's -31050..-31099 range: something is wrong with the account the payer typed, and
     * the answer names that account field in {@code data}.
     */
    public boolean isAccountError() {
        return code <= -31050 && code >= -31099;
    }
}
