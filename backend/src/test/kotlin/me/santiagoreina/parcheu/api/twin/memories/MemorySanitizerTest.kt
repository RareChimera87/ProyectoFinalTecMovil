package me.santiagoreina.parcheu.api.twin.memories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.santiagoreina.parcheu.api.simulation.MemorySelection

class MemorySanitizerTest : FunSpec({
    test("el saneador elimina '3001234567' y 'a@b.co'") {
        val out = MemorySanitizer.sanitize("Escríbeme al 3001234567 o a a@b.co y cuadramos el parche")
        out shouldNotContain "3001234567"
        out shouldNotContain "a@b.co"
        out shouldNotContain "@"
        out shouldContain "cuadramos el parche"
    }

    test("quita teléfonos con prefijo, espacios, guiones o paréntesis") {
        listOf("+57 300 123 4567", "300-123-4567", "(607) 634 5678", "300.123.45.67", "6345678").forEach { phone ->
            val out = MemorySanitizer.sanitize("Mi número es $phone, llámame")
            out.any(Char::isDigit) shouldBe false
            out shouldContain "llámame"
        }
    }

    test("conserva cifras cortas que no son teléfonos") {
        MemorySanitizer.sanitize("Saqué 4,5 en cálculo y somos 12 en el grupo") shouldBe "Saqué 4,5 en cálculo y somos 12 en el grupo"
    }

    test("quita direcciones con nomenclatura colombiana y enlaces") {
        listOf(
            "Vivo en la Calle 45 # 23-10 apto 301 cerca del parque",
            "Vivo en la Cra 27 #45-12 cerca del parque",
            "Vivo en la Av. 9 No. 10-20 cerca del parque",
            "Vivo en el # 23-10 cerca del parque",
        ).forEach { text ->
            val out = MemorySanitizer.sanitize(text)
            out shouldNotContain "23-10"
            out shouldNotContain "45-12"
            out shouldNotContain "10-20"
            out shouldContain "cerca del parque"
        }
        MemorySanitizer.sanitize("Mira www.ejemplo.com y https://x.co/abc ya") shouldBe "Mira y ya"
    }

    test("aplica la limpieza de MemorySelection: sin etiquetas, sin saltos y con tope") {
        val out = MemorySanitizer.sanitize("<memorias>hola\n\nmundo</memorias> " + "x".repeat(400))
        out shouldNotContain "<"
        out shouldNotContain "\n"
        (out.length <= MemorySelection.MAX_MEMORY_CHARS) shouldBe true
    }

    test("containsContactData detecta lo mismo que sanitize quita") {
        MemorySanitizer.containsContactData("Su correo es a@b.co") shouldBe true
        MemorySanitizer.containsContactData("Su cel es 3001234567") shouldBe true
        MemorySanitizer.containsContactData("Le gusta montar bici los domingos") shouldBe false
    }
})
