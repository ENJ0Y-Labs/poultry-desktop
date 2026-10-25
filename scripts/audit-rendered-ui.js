(function auditRenderedPomasAccessibility() {
  const essentialSelector = [
    "label", "button", "nav", "[role='status']", "[role='alert']",
    ".error", ".severity", ".nav-item", ".page-tab", ".row.header",
    ".metric-card span", ".system-pill", ".node-status strong", "h1", "h2", "h3",
    "th", "kbd", "input", "select", "textarea"
  ].join(",");

  function parseColor(value) {
    const match = value.match(/rgba?\(([^)]+)\)/i);
    if (!match) return null;
    const parts = match[1].split(",").map((part) => Number.parseFloat(part.trim()));
    if (parts.length < 3 || parts.slice(0, 3).some(Number.isNaN)) return null;
    return { r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 && !Number.isNaN(parts[3]) ? parts[3] : 1 };
  }

  function blend(foreground, background) {
    const alpha = foreground.a;
    return {
      r: foreground.r * alpha + background.r * (1 - alpha),
      g: foreground.g * alpha + background.g * (1 - alpha),
      b: foreground.b * alpha + background.b * (1 - alpha),
      a: 1
    };
  }

  function effectiveBackground(element) {
    const chain = [];
    for (let node = element; node; node = node.parentElement) chain.push(node);
    chain.reverse();
    let background = parseColor(getComputedStyle(document.documentElement).backgroundColor) || { r: 255, g: 255, b: 255, a: 1 };
    for (const node of chain) {
      const color = parseColor(getComputedStyle(node).backgroundColor);
      if (color && color.a > 0) background = blend(color, background);
    }
    return background;
  }

  function luminance(color) {
    const linear = [color.r, color.g, color.b].map((value) => {
      const channel = value / 255;
      return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
  }

  function ratio(foreground, background) {
    const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
    return (values[0] + 0.05) / (values[1] + 0.05);
  }

  const textWalker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const rows = [];
  while (textWalker.nextNode()) {
    const textNode = textWalker.currentNode;
    const text = textNode.nodeValue.replace(/\s+/g, " ").trim();
    const element = textNode.parentElement;
    if (!text || !element || element.closest("script,style,noscript,svg,[aria-hidden='true']")) continue;
    if (!element.getClientRects().length) continue;
    const style = getComputedStyle(element);
    if (style.visibility === "hidden" || style.display === "none") continue;
    const foreground = parseColor(style.color);
    const background = effectiveBackground(element);
    if (!foreground || !background) continue;
    const composedForeground = foreground.a < 1 ? blend(foreground, background) : foreground;
    const fontSize = Number.parseFloat(style.fontSize) || 0;
    const fontWeight = Number.parseInt(style.fontWeight, 10) || 400;
    const largeText = fontSize >= 24 || (fontSize >= 18.67 && fontWeight >= 700);
    const minimumContrast = largeText ? 3 : 4.5;
    const contrast = ratio(composedForeground, background);
    const essential = Boolean(element.closest(essentialSelector));
    rows.push({
      text: text.slice(0, 100),
      element: element.tagName.toLowerCase() + (element.className && typeof element.className === "string" ? "." + element.className.trim().replace(/\s+/g, ".") : ""),
      fontSize,
      contrast: Number(contrast.toFixed(2)),
      minimumContrast,
      essential,
      contrastFail: contrast < minimumContrast,
      smallText: fontSize > 0 && fontSize < 12
    });
  }

  const contrastFailures = rows.filter((row) => row.contrastFail);
  const essentialContrastFailures = contrastFailures.filter((row) => row.essential);
  const smallText = rows.filter((row) => row.smallText);
  const essentialSmallText = smallText.filter((row) => row.essential);
  const result = {
    theme: document.documentElement.dataset.theme || "auto",
    viewport: window.innerWidth + "×" + window.innerHeight,
    visibleTextNodesMeasured: rows.length,
    contrastFailures: contrastFailures.length,
    essentialContrastFailures: essentialContrastFailures.length,
    textBelow12px: smallText.length,
    essentialTextBelow12px: essentialSmallText.length,
    contrastFailureExamples: contrastFailures.slice(0, 30),
    essentialSmallTextExamples: essentialSmallText.slice(0, 30),
    note: "Background gradients, filters, and element opacity can affect pixel-level contrast; verify listed findings visually. Run separately in Light and Dark themes."
  };
  console.group("Pomas rendered accessibility audit");
  console.table({
    theme: result.theme,
    viewport: result.viewport,
    visibleTextNodesMeasured: result.visibleTextNodesMeasured,
    contrastFailures: result.contrastFailures,
    essentialContrastFailures: result.essentialContrastFailures,
    textBelow12px: result.textBelow12px,
    essentialTextBelow12px: result.essentialTextBelow12px
  });
  if (contrastFailures.length) console.table(result.contrastFailureExamples);
  if (essentialSmallText.length) console.table(result.essentialSmallTextExamples);
  console.log(result.note);
  console.groupEnd();
  return result;
})();