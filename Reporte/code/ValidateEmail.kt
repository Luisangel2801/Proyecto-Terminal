package com.example.grafica

import java.util.regex.Pattern

class ValidateEmail {
    companion object {
        // Patrón de expresión regular para validar el formato del correo electrónico
        private val EMAIL_PATTERN: Pattern = Pattern.compile(
            "[a-zA-Z0-9+._%\\-]{1,256}" +            // Parte local
                    "@" +                                     // @ símbolo
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +      // Subdominio
                    "(" +
                    "\\." +                                   // Punto
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +      // Dominios adicionales
                    ")+"
        )

        // Método para validar el correo electrónico
        fun isEmail(email: String): Boolean {
            if (email.isBlank()) {
                return false
            }
            return EMAIL_PATTERN.matcher(email).matches()
        }
    }
}