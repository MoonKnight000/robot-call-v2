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
