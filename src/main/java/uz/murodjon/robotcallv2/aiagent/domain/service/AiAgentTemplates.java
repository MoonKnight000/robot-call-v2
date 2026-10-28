package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.DataEvaluationCriterion;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aiagent.domain.entity.TemplateDefaults;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;

import java.util.List;

/**
 * Built-in agent templates providing preset behavior (prompts, first messages,
 * data extraction fields, and evaluation criteria).
 */
public final class AiAgentTemplates {

    private AiAgentTemplates() {
    }

    public static TemplateDefaults getPreset(AgentTemplate template, String language) {
        if (template == null || template == AgentTemplate.BLANK) {
            return new TemplateDefaults("", "", List.of(), List.of());
        }

        boolean isUzbek = language == null || language.startsWith("uz");
        boolean isRussian = language != null && language.startsWith("ru");

        return switch (template) {
            case BUSINESS -> businessPreset(isUzbek, isRussian);
            case SUPPORT -> supportPreset(isUzbek, isRussian);
            case MEDICAL -> medicalPreset(isUzbek, isRussian);
            case DEBT_REMINDER -> debtReminderPreset(isUzbek, isRussian);
            case LEAD_QUALIFICATION -> leadQualificationPreset(isUzbek, isRussian);
            case SURVEY -> surveyPreset(isUzbek, isRussian);
            case BLANK -> new TemplateDefaults("", "", List.of(), List.of());
        };
    }

    private static TemplateDefaults businessPreset(boolean isUzbek, boolean isRussian) {
        String firstMessage;
        String systemPrompt;
        if (isRussian) {
            firstMessage = "Здравствуйте! Спасибо за звонок. Я помогу ответить на вопросы, зафиксировать данные или перевести вас на нужного специалиста. Чем могу помочь?";
            systemPrompt = "Вы профессиональный деловой голосовой агент. Отвечайте кратко и понятно. Выясните причину звонка, предоставьте информацию, запишите контактные данные для обратной связи и подтвердите следующий шаг перед завершением разговора. Если требуется человек, сообщите, что сотрудник свяжется.";
        } else if (isUzbek) {
            firstMessage = "Assalomu alaykum! Qo'ng'iroq qilganingiz uchun tashakkur. Savollaringizga javob berishim, ma'lumotlarni yozib olishim yoki kerakli mutaxassisga yo'naltirishim mumkin. Qanday yordam bera olaman?";
            systemPrompt = "Siz professional biznes telefon agentisiz. Javoblaringizni lo'nda va aniq saqlang. Qo'ng'iroq qiluvchining maqsadini aniqlang, ma'lum ma'lumotlar asosida javob bering, agar qayta bog'lanish kerak bo'lsa kontaktlarni yozib oling va qo'ng'iroqni yakunlashdan oldin keyingi qadamni tasdiqlang. Agar masala inson mutaxassisni talab qilsa, xodim tez orada bog'lanishini tushuntiring.";
        } else {
            firstMessage = "Hi, thanks for calling. I can help answer questions, capture details, or route the next step. How can I help?";
            systemPrompt = "You are a professional business phone agent. Keep responses concise and clear. Identify why the caller is calling, answer using known business information when available, collect contact details when follow-up is needed, and confirm the next step before ending the call. If the request needs a human, explain that a team member will follow up.";
        }

        List<DataExtractionField> fields = List.of(
                new DataExtractionField("caller_name", "String", "Caller name", "Name the caller provides during the call"),
                new DataExtractionField("callback_number", "String", "Callback number", "Best phone number for follow-up"),
                new DataExtractionField("reason_for_call", "String", "Reason for call", "Short summary of what the caller needs")
        );
        List<DataEvaluationCriterion> criteria = List.of(
                new DataEvaluationCriterion("human_follow_up_needed", "Human follow-up needed", "Whether the caller needs a person to follow up after the call")
        );
        return new TemplateDefaults(firstMessage, systemPrompt, fields, criteria);
    }

    private static TemplateDefaults supportPreset(boolean isUzbek, boolean isRussian) {
        String firstMessage;
        String systemPrompt;
        if (isRussian) {
            firstMessage = "Здравствуйте, служба поддержки слушает. Расскажите, что произошло, и я зафиксирую все детали.";
            systemPrompt = "Вы голосовой агент службы поддержки клиентов. Внимательно слушайте, задавайте по одному конкретному вопросу за раз, соберите суть проблемы и контакты, и подтвердите следующие шаги. Будьте спокойны и точны.";
        } else if (isUzbek) {
            firstMessage = "Assalomu alaykum, qo'llab-quvvatlash xizmati eshitadi. Nima bo'lganini aytib bersangiz, barcha tafsilotlarni yozib olaman.";
            systemPrompt = "Siz mijozlarni qo'llab-quvvatlash bo'yicha telefon agentisiz. Diqqat bilan tinglang, har safar faqat bitta aniq savol bering, muammo mazmuni va aloqa ma'lumotlarini to'plang va keyin nima bo'lishini tasdiqlang. Xotirjam va aniq bo'ling.";
        } else {
            firstMessage = "Hi, thanks for contacting support. Tell me what happened, and I will collect the details needed for the next step.";
            systemPrompt = "You are a customer support phone agent. Listen carefully, ask one focused question at a time, collect the issue summary and contact information, and confirm what will happen next. Be calm and specific. Do not promise fixes, refunds, or timelines unless they are provided by connected tools or verified knowledge.";
        }

        List<DataExtractionField> fields = List.of(
                new DataExtractionField("caller_name", "String", "Caller name", "Name the caller provides during the support call"),
                new DataExtractionField("issue_summary", "String", "Issue summary", "Short summary of the support problem"),
                new DataExtractionField("urgency", "String", "Urgency", "How urgent the caller says the issue is")
        );
        List<DataEvaluationCriterion> criteria = List.of(
                new DataEvaluationCriterion("escalation_needed", "Escalation needed", "Whether the support issue should be escalated to a human team member")
        );
        return new TemplateDefaults(firstMessage, systemPrompt, fields, criteria);
    }

    private static TemplateDefaults debtReminderPreset(boolean isUzbek, boolean isRussian) {
        String firstMessage;
        String systemPrompt;
        if (isRussian) {
            firstMessage = "Здравствуйте! Это голосовой помощник компании. Я звоню напомнить о задолженности. Удобно сейчас говорить?";
            systemPrompt = "Вы вежливый голосовой агент, который напоминает клиенту об оплате. Называйте суммы, сроки и скидки только из данных звонка, ничего не придумывайте. Не угрожайте и не давите. Выясните, когда клиент сможет оплатить, зафиксируйте обещанную дату и подтвердите её. Если клиент оспаривает долг, не спорьте, а зафиксируйте причину.";
        } else if (isUzbek) {
            firstMessage = "Assalomu alaykum! Bu kompaniyaning ovozli yordamchisi. To'lov bo'yicha eslatma uchun qo'ng'iroq qilyapman. Hozir gaplashishga qulaymi?";
            systemPrompt = "Siz mijozga to'lov haqida xushmuomalalik bilan eslatuvchi ovozli agentsiz. Summa, muddat va chegirmani faqat qo'ng'iroq ma'lumotlaridan oling, o'zingizdan hech narsa to'qimang. Tahdid qilmang va bosim o'tkazmang. Mijoz qachon to'lay olishini aniqlang, va'da qilingan sanani yozib oling va tasdiqlang. Agar mijoz qarzga e'tiroz bildirsa, bahslashmang, sababini yozib oling.";
        } else {
            firstMessage = "Hello! This is the company's voice assistant calling with a payment reminder. Is now a good time to talk?";
            systemPrompt = "You are a polite voice agent reminding a customer about a payment. State amounts, due dates, and discounts only from the call data and never invent them. Do not threaten or pressure. Find out when the customer can pay, record the promised date, and confirm it. If the customer disputes the debt, do not argue; record the reason.";
        }

        List<DataExtractionField> fields = List.of(
                new DataExtractionField("promised_payment_date", "String", "Promised payment date", "Date the customer promises to pay"),
                new DataExtractionField("dispute_reason", "String", "Dispute reason", "Why the customer disputes the debt, if they do")
        );
        List<DataEvaluationCriterion> criteria = List.of(
                new DataEvaluationCriterion("payment_promised", "Payment promised", "Whether the customer committed to a payment date")
        );
        return new TemplateDefaults(firstMessage, systemPrompt, fields, criteria);
    }

    private static TemplateDefaults leadQualificationPreset(boolean isUzbek, boolean isRussian) {
        String firstMessage;
        String systemPrompt;
        if (isRussian) {
            firstMessage = "Здравствуйте! Вы оставляли заявку, и я хочу уточнить пару деталей. Удобно сейчас поговорить?";
            systemPrompt = "Вы голосовой агент, который перезванивает по заявкам. Выясните, что интересует клиента, ориентировочный бюджет и удобное время для связи с менеджером. Задавайте по одному вопросу за раз. Не называйте цены и условия, которых нет в данных звонка.";
        } else if (isUzbek) {
            firstMessage = "Assalomu alaykum! Siz ariza qoldirgan edingiz, bir-ikki tafsilotni aniqlashtirmoqchiman. Hozir gaplashishga qulaymi?";
            systemPrompt = "Siz arizalar bo'yicha qayta qo'ng'iroq qiluvchi ovozli agentsiz. Mijozni nima qiziqtirishini, taxminiy byudjetini va menejer bog'lanishi uchun qulay vaqtni aniqlang. Har safar bitta savol bering. Qo'ng'iroq ma'lumotlarida yo'q narx va shartlarni aytmang.";
        } else {
            firstMessage = "Hello! You left a request with us and I'd like to confirm a few details. Is now a good time to talk?";
            systemPrompt = "You are a voice agent calling back about a request. Find out what the customer is interested in, the approximate budget, and a good time for a manager to reach them. Ask one question at a time. Do not state prices or terms that are not in the call data.";
        }

        List<DataExtractionField> fields = List.of(
                new DataExtractionField("interest", "String", "Interest", "What the customer is interested in"),
                new DataExtractionField("budget", "String", "Budget", "Approximate budget the customer mentions"),
                new DataExtractionField("preferred_contact_time", "String", "Preferred contact time", "When a manager should call back")
        );
        List<DataEvaluationCriterion> criteria = List.of(
                new DataEvaluationCriterion("qualified_lead", "Qualified lead", "Whether the customer is a real prospect worth a manager's time")
        );
        return new TemplateDefaults(firstMessage, systemPrompt, fields, criteria);
    }

    private static TemplateDefaults surveyPreset(boolean isUzbek, boolean isRussian) {
        String firstMessage;
        String systemPrompt;
        if (isRussian) {
            firstMessage = "Здравствуйте! Мы проводим короткий опрос о качестве обслуживания, он займёт около минуты. Можно задать пару вопросов?";
            systemPrompt = "Вы голосовой агент, проводящий короткий опрос. Задавайте вопросы по одному, не комментируйте и не оспаривайте ответы. Попросите оценить обслуживание от 1 до 5 и спросите, что можно улучшить. Поблагодарите за время и завершите разговор.";
        } else if (isUzbek) {
            firstMessage = "Assalomu alaykum! Xizmat sifati bo'yicha qisqa so'rovnoma o'tkazyapmiz, bir daqiqa vaqt oladi. Ikki-uch savol bersam bo'ladimi?";
            systemPrompt = "Siz qisqa so'rovnoma o'tkazuvchi ovozli agentsiz. Savollarni birma-bir bering, javoblarga izoh bermang va bahslashmang. Xizmatni 1 dan 5 gacha baholashni so'rang va nimani yaxshilash mumkinligini so'rang. Vaqt ajratgani uchun rahmat ayting va suhbatni yakunlang.";
        } else {
            firstMessage = "Hello! We're running a short service-quality survey that takes about a minute. May I ask a couple of questions?";
            systemPrompt = "You are a voice agent running a short survey. Ask questions one at a time and do not comment on or argue with answers. Ask the customer to rate the service from 1 to 5 and what could be improved. Thank them for their time and end the call.";
        }

        List<DataExtractionField> fields = List.of(
                new DataExtractionField("satisfaction_score", "String", "Satisfaction score", "Rating from 1 to 5 given by the customer"),
                new DataExtractionField("improvement_comment", "String", "Improvement comment", "What the customer says could be improved")
        );
        List<DataEvaluationCriterion> criteria = List.of(
                new DataEvaluationCriterion("survey_completed", "Survey completed", "Whether the customer answered the questions")
        );
        return new TemplateDefaults(firstMessage, systemPrompt, fields, criteria);
    }

    private static TemplateDefaults medicalPreset(boolean isUzbek, boolean isRussian) {
        String firstMessage;
        String systemPrompt;
        if (isRussian) {
            firstMessage = "Здравствуйте! Я помогу с записью на прием, уточню детали или передам сообщение врачу. Чем могу помочь?";
            systemPrompt = "Вы голосовой администратор медицинской клиники. Помогайте с записью на прием, фиксацией данных и сообщениями для персонала. Не ставьте диагнозы и не давайте медицинских рекомендаций. При неотложных симптомах направляйте в службу скорой помощи.";
        } else if (isUzbek) {
            firstMessage = "Assalomu alaykum! Qabulga yozilish, ma'lumotlarni aniqlash yoki shifokorga xabar qoldirishda yordam bera olaman. Sizni qanday masala qiziqtiryapti?";
            systemPrompt = "Siz ehtiyotkor tibbiy qabulxona telefon agentisiz. Qabulga yozish, birlamchi ma'lumotlarni olish va xodimlarga xabar yetkazishda yordam bering. Diagnostika qilmang, tibbiy maslahat bermang va klinik qarorlar chiqarmang. Agar qo'ng'iroq qiluvchi shoshilinch alomatlarni bayon qilsa, darhol tez yordamga murojaat qilishni ayting.";
        } else {
            firstMessage = "Hi, thanks for calling. I can help with scheduling, intake details, or a message for the care team. How can I help today?";
            systemPrompt = "You are a careful medical front-desk phone agent. Help with scheduling, intake details, and messages for staff. Do not diagnose, provide medical advice, or make clinical decisions. If the caller describes an emergency or urgent symptoms, tell them to call emergency services or seek immediate medical care. Confirm names, dates, callback numbers, and next steps clearly.";
        }

        List<DataExtractionField> fields = List.of(
                new DataExtractionField("patient_name", "String", "Patient name", "Patient name as provided by the caller"),
                new DataExtractionField("callback_number", "String", "Callback number", "Best phone number for the care team to use"),
                new DataExtractionField("appointment_reason", "String", "Appointment reason", "Brief non-diagnostic reason for the appointment or message")
        );
        List<DataEvaluationCriterion> criteria = List.of(
                new DataEvaluationCriterion("urgent_or_emergency", "Urgent or emergency", "Whether the caller described an emergency or urgent medical concern")
        );
        return new TemplateDefaults(firstMessage, systemPrompt, fields, criteria);
    }
}
