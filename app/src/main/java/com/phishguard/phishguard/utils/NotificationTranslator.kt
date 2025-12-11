package com.phishguard.phishguard.utils

import android.content.Context
import java.util.Locale

/**
 * Dynamic notification translator that detects system language
 * and translates notification text on-the-fly
 */
class NotificationTranslator(private val context: Context) {
    
    private val systemLanguage: String by lazy {
        Locale.getDefault().language.lowercase()
    }
    
    /**
     * Translation maps for different languages
     */
    private val translations = mapOf(
        // Spanish
        "es" to mapOf(
            "phishing_detected" to "🛑 PHISHING DETECTADO - NO CONTINÚE",
            "suspicious_site" to "⚠️ Advertencia de Sitio Sospechoso",
            "danger_content" to "PELIGRO: %s es probablemente un sitio de phishing",
            "warning_content" to "Advertencia: %s muestra patrones sospechosos",
            "risk_level" to "Nivel de Riesgo: %d%%",
            "why_flagged" to "Por qué está marcado:",
            "do_not_enter" to "⚠️ ¡NO ingrese contraseñas o información personal!",
            "channel_danger" to "Alertas de Phishing",
            "channel_warning" to "Advertencias de Seguridad",
            "channel_description" to "Alertas para amenazas detectadas"
        ),
        
        // French
        "fr" to mapOf(
            "phishing_detected" to "🛑 PHISHING DÉTECTÉ - NE PAS CONTINUER",
            "suspicious_site" to "⚠️ Avertissement de Site Suspect",
            "danger_content" to "DANGER: %s est probablement un site de phishing",
            "warning_content" to "Avertissement: %s montre des motifs suspects",
            "risk_level" to "Niveau de Risque: %d%%",
            "why_flagged" to "Pourquoi c'est signalé:",
            "do_not_enter" to "⚠️ NE saisissez PAS de mots de passe ou d'informations personnelles!",
            "channel_danger" to "Alertes de Phishing",
            "channel_warning" to "Avertissements de Sécurité",
            "channel_description" to "Alertes pour les menaces détectées"
        ),
        
        // German
        "de" to mapOf(
            "phishing_detected" to "🛑 PHISHING ERKANNT - NICHT FORTFAHREN",
            "suspicious_site" to "⚠️ Verdächtige Website-Warnung",
            "danger_content" to "GEFAHR: %s ist wahrscheinlich eine Phishing-Website",
            "warning_content" to "Warnung: %s zeigt verdächtige Muster",
            "risk_level" to "Risikostufe: %d%%",
            "why_flagged" to "Warum dies markiert ist:",
            "do_not_enter" to "⚠️ Geben Sie KEINE Passwörter oder persönlichen Daten ein!",
            "channel_danger" to "Phishing-Warnungen",
            "channel_warning" to "Sicherheitswarnungen",
            "channel_description" to "Warnungen für erkannte Bedrohungen"
        ),
        
        // Portuguese
        "pt" to mapOf(
            "phishing_detected" to "🛑 PHISHING DETECTADO - NÃO PROSSIGA",
            "suspicious_site" to "⚠️ Aviso de Site Suspeito",
            "danger_content" to "PERIGO: %s é provavelmente um site de phishing",
            "warning_content" to "Aviso: %s mostra padrões suspeitos",
            "risk_level" to "Nível de Risco: %d%%",
            "why_flagged" to "Por que foi sinalizado:",
            "do_not_enter" to "⚠️ NÃO digite senhas ou informações pessoais!",
            "channel_danger" to "Alertas de Phishing",
            "channel_warning" to "Avisos de Segurança",
            "channel_description" to "Alertas para ameaças detectadas"
        ),
        
        // Italian
        "it" to mapOf(
            "phishing_detected" to "🛑 PHISHING RILEVATO - NON PROCEDERE",
            "suspicious_site" to "⚠️ Avviso Sito Sospetto",
            "danger_content" to "PERICOLO: %s è probabilmente un sito di phishing",
            "warning_content" to "Avviso: %s mostra schemi sospetti",
            "risk_level" to "Livello di Rischio: %d%%",
            "why_flagged" to "Perché è contrassegnato:",
            "do_not_enter" to "⚠️ NON inserire password o informazioni personali!",
            "channel_danger" to "Avvisi di Phishing",
            "channel_warning" to "Avvisi di Sicurezza",
            "channel_description" to "Avvisi per minacce rilevate"
        ),
        
        // Russian
        "ru" to mapOf(
            "phishing_detected" to "🛑 ФИШИНГ ОБНАРУЖЕН - НЕ ПРОДОЛЖАЙТЕ",
            "suspicious_site" to "⚠️ Предупреждение о Подозрительном Сайте",
            "danger_content" to "ОПАСНОСТЬ: %s вероятно фишинговый сайт",
            "warning_content" to "Предупреждение: %s показывает подозрительные признаки",
            "risk_level" to "Уровень Риска: %d%%",
            "why_flagged" to "Почему помечено:",
            "do_not_enter" to "⚠️ НЕ вводите пароли или личную информацию!",
            "channel_danger" to "Предупреждения о Фишинге",
            "channel_warning" to "Предупреждения Безопасности",
            "channel_description" to "Предупреждения об обнаруженных угрозах"
        ),
        
        // Japanese
        "ja" to mapOf(
            "phishing_detected" to "🛑 フィッシング検出 - 続行しないでください",
            "suspicious_site" to "⚠️ 疑わしいサイトの警告",
            "danger_content" to "危険: %s はフィッシングサイトの可能性があります",
            "warning_content" to "警告: %s は疑わしいパターンを示しています",
            "risk_level" to "リスクレベル: %d%%",
            "why_flagged" to "フラグが立てられた理由:",
            "do_not_enter" to "⚠️ パスワードや個人情報を入力しないでください！",
            "channel_danger" to "フィッシング警告",
            "channel_warning" to "セキュリティ警告",
            "channel_description" to "検出された脅威のアラート"
        ),
        
        // Chinese Simplified
        "zh" to mapOf(
            "phishing_detected" to "🛑 检测到钓鱼网站 - 请勿继续",
            "suspicious_site" to "⚠️ 可疑网站警告",
            "danger_content" to "危险：%s 可能是钓鱼网站",
            "warning_content" to "警告：%s 显示可疑模式",
            "risk_level" to "风险等级：%d%%",
            "why_flagged" to "标记原因：",
            "do_not_enter" to "⚠️ 请勿输入密码或个人信息！",
            "channel_danger" to "钓鱼警报",
            "channel_warning" to "安全警告",
            "channel_description" to "检测到威胁的警报"
        ),
        
        // Hindi
        "hi" to mapOf(
            "phishing_detected" to "🛑 फिशिंग का पता चला - आगे न बढ़ें",
            "suspicious_site" to "⚠️ संदिग्ध साइट चेतावनी",
            "danger_content" to "खतरा: %s संभवतः एक फिशिंग साइट है",
            "warning_content" to "चेतावनी: %s संदिग्ध पैटर्न दिखाता है",
            "risk_level" to "जोखिम स्तर: %d%%",
            "why_flagged" to "यह क्यों फ्लैग किया गया:",
            "do_not_enter" to "⚠️ पासवर्ड या व्यक्तिगत जानकारी दर्ज न करें!",
            "channel_danger" to "फिशिंग अलर्ट",
            "channel_warning" to "सुरक्षा चेतावनी",
            "channel_description" to "पहचाने गए खतरों के लिए अलर्ट"
        ),
        
        // Arabic
        "ar" to mapOf(
            "phishing_detected" to "🛑 تم اكتشاف التصيد - لا تتابع",
            "suspicious_site" to "⚠️ تحذير من موقع مشبوه",
            "danger_content" to "خطر: %s على الأرجح موقع تصيد",
            "warning_content" to "تحذير: %s يظهر أنماط مشبوهة",
            "risk_level" to "مستوى المخاطر: %d%%",
            "why_flagged" to "لماذا تم وضع علامة:",
            "do_not_enter" to "⚠️ لا تدخل كلمات المرور أو المعلومات الشخصية!",
            "channel_danger" to "تنبيهات التصيد",
            "channel_warning" to "تحذيرات الأمان",
            "channel_description" to "تنبيهات للتهديدات المكتشفة"
        )
    )
    
    /**
     * Get translated text for the system language
     */
    fun translate(key: String, vararg args: Any): String {
        val languageMap = translations[systemLanguage]
        val translatedText = languageMap?.get(key)
        
        return if (translatedText != null) {
            // Apply formatting if arguments provided
            if (args.isNotEmpty()) {
                String.format(translatedText, *args)
            } else {
                translatedText
            }
        } else {
            // Fallback to English
            getEnglishFallback(key, *args)
        }
    }
    
    /**
     * English fallback translations
     */
    private fun getEnglishFallback(key: String, vararg args: Any): String {
        val englishTexts = mapOf(
            "phishing_detected" to "🛑 PHISHING DETECTED - DO NOT PROCEED",
            "suspicious_site" to "⚠️ Suspicious Site Warning",
            "danger_content" to "DANGER: %s is likely a phishing site",
            "warning_content" to "Warning: %s shows suspicious patterns",
            "risk_level" to "Risk Level: %d%%",
            "why_flagged" to "Why this is flagged:",
            "do_not_enter" to "⚠️ DO NOT enter passwords or personal information!",
            "channel_danger" to "Phishing Alerts",
            "channel_warning" to "Security Warnings",
            "channel_description" to "Alerts for detected threats"
        )
        
        val text = englishTexts[key] ?: key
        return if (args.isNotEmpty()) {
            String.format(text, *args)
        } else {
            text
        }
    }
    
    /**
     * Get system language for debugging
     */
    fun getCurrentLanguage(): String = systemLanguage
    
    /**
     * Check if translation is available for current language
     */
    fun isTranslationAvailable(): Boolean = translations.containsKey(systemLanguage)
}