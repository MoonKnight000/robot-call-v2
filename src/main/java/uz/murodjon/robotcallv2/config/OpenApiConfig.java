package uz.murodjon.robotcallv2.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.murodjon.robotcallv2.security.ClientAddress;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;

import java.util.List;

/**
 * Global OpenAPI 3 / Swagger documentation configuration.
 *
 * <p>Centralizes API metadata, security schemes (Bearer JWT and X-Api-Key), server definitions,
 * and ignores framework-injected parameter annotations ({@link CurrentCompanyId}, {@link ClientAddress})
 * so that endpoints render cleanly without exposing internal parameters in the Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "BearerAuth";
    public static final String API_KEY_AUTH = "ApiKeyAuth";

    static {
        // Hide internal parameters resolved by Spring MVC ArgumentResolvers from Swagger schema
        SpringDocUtils.getConfig().addAnnotationsToIgnore(
                CurrentCompanyId.class,
                ClientAddress.class
        );
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Voice Agent & Call Center Platform API")
                        .version("2.0.0")
                        .description("""
                                ### AI Voice Agent & Call Center Platform REST API

                                Automatic outbound campaign dialer and conversational AI voice assistant platform with Asterisk ARI, Silero VAD, streaming STT/TTS, and LLM dialog engines.

                                #### 1. Autentifikatsiya (Authentication)
                                Har bir so'rov ikkita usuldan biri orqali autentifikatsiya qilinadi:
                                * **Bearer JWT Token**: Foydalanuvchilar (operator/admin) uchun `Authorization: Bearer <accessToken>` sarlavhasi. Token `POST /api/auth/login` orqali olinadi.
                                * **X-Api-Key**: Tashqi tizimlar va integratsiyalar uchun kompaniyaning `X-Api-Key: <key>` sarlavhasi.

                                #### 2. Ko'p ijarachilik (Multi-Tenancy)
                                Tizim to'liq multi-tenant arxitekturada ishlaydi. Har bir foydalanuvchi va API-kalit o'z kompaniyasiga tegishli ma'lumotlar bilan chegaralangan.

                                #### 3. Standart Javob Formati (Response Envelope)
                                Barcha muvaffaqiyatli va xatolik javoblari standart `ResponseData<T>` konvertiga o'ralgan:
                                ```json
                                {
                                  "data": { ... },
                                  "message": "Operatsiya muvaffaqiyatli",
                                  "messageCode": null,
                                  "accept": true,
                                  "status": 200,
                                  "timestamp": 1711972800000
                                }
                                ```
                                """)
                        .contact(new Contact()
                                .name("Uysot Voice Platform Support")
                                .email("support@uysot.uz")
                                .url("https://uysot.uz"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("/").description("Current Server (Relative)")
                ))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .name("Authorization")
                                .description("JWT Access Token for user authentication (e.g. from /api/auth/login)"))
                        .addSecuritySchemes(API_KEY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")
                                .description("Company API Key for machine-to-machine / external integration")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_AUTH))
                .tags(List.of(
                        new Tag().name("Auth").description("Foydalanuvchi autentifikatsiyasi, tokenlar, 2FA TOTP va parolni tiklash"),
                        new Tag().name("Users").description("Foydalanuvchilar ro'yxati, taklifnoma, rol biriktirish va bloklash"),
                        new Tag().name("Roles").description("Rollar (RBAC) va ruxsatnomalar katalogi"),
                        new Tag().name("Companies").description("Kompaniya profili, telefon sozlamalari va ish tartibi"),
                        new Tag().name("AI Agents").description("Ovozli sun'iy intellekt agentlari, xarakter, prompt va ovoz sozlamalari"),
                        new Tag().name("AI Models").description("LLM, STT va TTS provayderlari hamda modellar sozlamalari"),
                        new Tag().name("Scenarios").description("Vizual dialog ssenariylari, graf tugunlari va ssenariy testi"),
                        new Tag().name("Knowledge Base").description("RAG bilimlar bazasi, manbalar va semantik qidiruv hujjatlari"),
                        new Tag().name("Campaigns").description("Chiqish qo'ng'iroqlari kompaniyalari, nishon raqamlar va CSV yuklash"),
                        new Tag().name("Calls").description("Qo'ng'iroqlar jurnali (CDR), audio yozuvlar, transkript va tahlillar"),
                        new Tag().name("Live").description("Jonli qo'ng'iroqlarni kuzatish (SSE), tinglash, shivirlash va aralashish"),
                        new Tag().name("Inbound Routes").description("Kiruvchi raqamlar, yo'naltirish qoidalari va IVR menyular"),
                        new Tag().name("Call Queues").description("Operator navbatlari, qo'ng'iroqlarni taqsimlash va guruhlar"),
                        new Tag().name("Operators").description("Operator hisoblari, navbat a'zoligi va SIP webphone ma'lumotlari"),
                        new Tag().name("CRM Webhooks").description("CRM integratsiyalari va kiruvchi webhooklar (Uysot, AmoCRM, Bitrix24)"),
                        new Tag().name("Contacts").description("Kontaktlar kitobi, ro'yxatlar, teglar va import"),
                        new Tag().name("Do Not Call").description("Chiqish qo'ng'iroqlari taqiqlangan raqamlar (DNC) qora ro'yxati"),
                        new Tag().name("Missed Calls").description("Javobsiz qolgan va uzilgan qo'ng'iroqlar ish ro'yxati"),
                        new Tag().name("Call Reviews").description("Qo'ng'iroqlar sifatini baholash (QA), inson va AI reytinglari"),
                        new Tag().name("Reports").description("Aloqa markazi analitikasi, KPI ko'rsatkichlari va hisobot eksporti"),
                        new Tag().name("Widgets").description("Veb-sayt ovozli qo'ng'iroq va chat vidjeti sozlamalari"),
                        new Tag().name("Chat").description("Veb vidjet va Telegram bot orqali jonli muloqot xabarlari"),
                        new Tag().name("Billing").description("Kompaniya balansi, tariflar va to'lovlar (Payme, Click)"),
                        new Tag().name("API Keys").description("Dasturiy kirish uchun API-kalitlar va ularning huquqlari"),
                        new Tag().name("Files").description("Fayllar va audio yozuvlar ombori"),
                        new Tag().name("Tools").description("AI agentlar chaqirishi mumkin bo'lgan tashqi OpenAPI vositalari")
                ));
    }
}
