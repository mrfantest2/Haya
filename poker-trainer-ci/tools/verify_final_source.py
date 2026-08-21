from pathlib import Path

activity = Path('app/src/main/java/com/fantest/pokervision/VisionActivityV025.java').read_text(encoding='utf-8')
math = Path('app/src/main/java/com/fantest/pokervision/PokerMath.java').read_text(encoding='utf-8')
workflow = Path('../.github/workflows/poker-vision-v0.2.0-build.yml').read_text(encoding='utf-8')

checks = {
    'hole-card tap opens picker directly': 'selectHole(index); showManualPicker();' in activity,
    'board-card tap opens picker directly': 'selectBoard(index); showManualPicker();' in activity,
    'manual picker blocks already-selected card': 'Already selected — choose another card' in activity,
    'guide greys already-selected card': 'face.setAlpha(used ? .22f : 1f);' in activity,
    'scanner greys already-selected candidate': 'b.setAlpha(used ? .28f : 1f);' in activity,
    'UI permits nine opponents': 'if (opponents < 9)' in activity,
    'UI presents player total': 'playersTotal = opponents + 1' in activity,
    'math permits nine opponents': 'opponents > 9' in math,
    'v0.2.8 patch is not applied during build': 'apply_v028_card_tap_picker.py' not in workflow,
    'v0.2.9 patch is not applied during build': 'apply_v029_card_availability.py' not in workflow,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('Final-source verification failed:\n- ' + '\n- '.join(failed))

print('Final source is self-contained: interaction, card availability, and 10-player support are baked in.')
