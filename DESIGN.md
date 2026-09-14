# Design System — RutaExpress Frontend

<!-- impeccable:design-schema 1 -->

## Visual Identity & Palette

La identidad visual está inspirada en la señalética y equipamiento de logística industrial de alto rendimiento, empleando una combinación calibrada de **amarillo de advertencia/señalización y escalas de grises antracita y pizarra**.

### Tokens de Color

```css
:root {
  /* Yellow Palette (Industrial Accent & Highlights) */
  --color-yellow-50:  #FFFBEB;
  --color-yellow-100: #FEF3C7;
  --color-yellow-200: #FDE68A;
  --color-yellow-300: #FCD34D;
  --color-yellow-400: #FBBF24;
  --color-yellow-500: #F59E0B; /* Primario de marca */
  --color-yellow-600: #D97706; /* Contraste accesible en fondos claros */
  --color-yellow-700: #B45309;
  --color-yellow-900: #78350F;

  /* Grayscale / Slate Palette (Neutral Structure & Depth) */
  --color-gray-950: #090D16; /* Fondo ultra oscuro */
  --color-gray-900: #0F172A; /* Fondo base de tarjetas y barras */
  --color-gray-850: #141E33; /* Superficies elevadas */
  --color-gray-800: #1E293B; /* Bordes estructurales oscuros */
  --color-gray-700: #334155; /* Divisores y bordes activos */
  --color-gray-600: #475569; /* Iconos y controles tenues */
  --color-gray-500: #64748B; /* Textos secundarios en modo claro */
  --color-gray-400: #94A3B8; /* Textos secundarios en modo oscuro */
  --color-gray-300: #CBD5E1; /* Textos de soporte */
  --color-gray-200: #E2E8F0; /* Bordes claros */
  --color-gray-100: #F1F5F9; /* Fondos claros alternos */
  --color-gray-50:  #F8FAFC; /* Superficie clara base */

  /* Semantic Highlights */
  --color-success: #10B981;
  --color-warning: #F59E0B;
  --color-danger:  #EF4444;
  --color-info:    #38BDF8;

  /* Elevation & Shadows */
  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.25);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.3), 0 2px 4px -2px rgba(0, 0, 0, 0.25);
  --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.4), 0 4px 6px -4px rgba(0, 0, 0, 0.3);
  --shadow-yellow: 0 0 15px rgba(245, 158, 11, 0.15);
}
```

---

## Typography

- **Fuente Principal:** Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif.
- **Datos Numéricos y Códigos:** `font-variant-numeric: tabular-nums` y fuentes monoespaciadas (`ui-monospace`, "SF Mono", Menlo, Consolas) para códigos de tracking, SKUs, pesos y métricas porcentuales.
- **Escala:**
  - Display / Título principal: `1.75rem (28px)` - `font-weight: 700`
  - Encabezados de sección: `1.25rem (20px)` - `font-weight: 600`
  - Subtítulos: `0.95rem (15px)` - `font-weight: 500`
  - Texto base / tablas: `0.875rem (14px)` - `font-weight: 400 / 500`
  - Etiquetas y metadatos: `0.75rem (12px)` - `font-weight: 600`, `letter-spacing: 0.05em`, `text-transform: uppercase`.

---

## Principles & Guidelines (Craft Floor)

1. **Jerarquía Clara y Alta Densidad:** Diseño concebido para monitores de operaciones logísticas, con tablas compactas, métricas visuales con barras de capacidad y badges de estado unificados.
2. **Iconografía SVG Nítida:** No se usan emojis unicode para representar estados o tipos de vehículos; cada indicador cuenta con SVGs geométricos precisos (camiones, furgones, motos, alertas, paquetes).
3. **Contraste Accesible:**
   - Textos amarillos sobre fondos oscuros usan tonos luminosos (`#FBBF24` o `#FCD34D`).
   - Textos sobre fondos amarillos usan gris 950 (`#090D16`) garantizando un ratio superior a 7:1.
4. **Superficies del Navegador Personalizadas:**
   - Selección de texto en amarillo suave (`background: rgba(245, 158, 11, 0.3)`).
   - Scrollbars estilizadas con pistas en gris oscuro y cursores redondeados en gris 700 / amarillo.
   - Anillos de foco (`focus-visible`) nítidos con `outline: 2px solid var(--color-yellow-400)`.
