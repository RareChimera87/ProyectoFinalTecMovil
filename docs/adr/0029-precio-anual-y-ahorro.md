# ADR-0029: Texto de ahorro del plan anual

- **Estado:** **Propuesta, pendiente de confirmación del usuario antes del 10 nov** (arranque de E7.3) · 22 oct 2026
- **Features:** E7.2, E7.3

## Contexto
El requisito y el mockup dicen $5.000 COP/mes y $45.000 COP/año con '2 meses gratis' y '$3.750/mes'. Pero 12 × $5.000 − $45.000 = $15.000 = **3 meses**. El requisito también pide tono de venta honesto, y los mockups mandan sobre la identidad visual.

## Decisión propuesta
- Mantener los precios pedidos ($5.000 y $45.000) y mostrar '$3.750/mes' y 'Ahorras $15.000 al año', calculados siempre desde `ProductDetails`, **sin** la frase de meses gratis.

## Alternativas
- **(a) '3 meses gratis' calculado:** exacto, pero cambia el texto del mockup.
- **(b) Conservar '2 meses gratis' subiendo el anual a $50.000:** respeta el texto pero rompe '$3.750/mes' (sería $4.167/mes).
- **(c) Conservar '2 meses gratis' con $45.000:** inexacto; descartada por tono honesto.

## Consecuencias
- Cualquiera de las opciones se implementa igual (cálculo desde `ProductDetails`); solo cambia la cadena y el test de E7.3.
- Si el usuario no responde antes del 10 nov, se implementa la propuesta por defecto.
