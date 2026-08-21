from pathlib import Path

path = Path('app/src/main/java/com/fantest/pokervision/VisionActivityV025.java')
text = path.read_text(encoding='utf-8')

replacements = {
    'holeViews[i].setOnClickListener(v -> selectHole(index));':
        'holeViews[i].setOnClickListener(v -> { selectHole(index); showManualPicker(); });',
    'boardViews[i].setOnClickListener(v -> selectBoard(index));':
        'boardViews[i].setOnClickListener(v -> { selectBoard(index); showManualPicker(); });',
    'modeHint.setText(arabic ? "اضغط أي خانة لاختيارها • ضغطة مطولة لمسح البطاقة." : "Tap any slot to select it • long-press a filled slot to clear.");':
        'modeHint.setText(arabic ? "اضغط أي بطاقة أو خانة لاختيارها مباشرة • ضغطة مطولة لمسح البطاقة." : "Tap any card or empty slot to choose it directly • long-press a filled slot to clear.");',
    'resultDetail.setText(arabic ? "اضغط خانة بطاقة، ثم استخدم المسح أو اليدوي أو الدليل." : "Tap a card slot, then use Scan, Manual or Guide.");':
        'resultDetail.setText(arabic ? "اضغط أي بطاقة أو خانة فارغة لفتح اختيار البطاقة مباشرة." : "Tap any card or empty slot to open the card picker directly.");',
}

for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'Expected source fragment not found: {old}')
    text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')

# Hard assertions so CI cannot silently ship the old behavior.
patched = path.read_text(encoding='utf-8')
assert 'selectHole(index); showManualPicker();' in patched
assert 'selectBoard(index); showManualPicker();' in patched
print('v0.2.8 card-tap picker patch applied successfully')
