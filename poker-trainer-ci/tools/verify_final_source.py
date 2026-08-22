from pathlib import Path

activity_path = Path('app/src/main/java/com/fantest/pokervision/VisionActivityV030.java')
activity = activity_path.read_text(encoding='utf-8') if activity_path.exists() else ''
math = Path('app/src/main/java/com/fantest/pokervision/PokerMath.java').read_text(encoding='utf-8')
gradle = Path('app/build.gradle').read_text(encoding='utf-8')
workflow = Path('../.github/workflows/poker-vision-v0.2.0-build.yml').read_text(encoding='utf-8')

checks = {
    'versionCode 14': 'versionCode 14' in gradle,
    'versionName 0.3.0': "versionName '0.3.0'" in gradle,
    'OpenCV 4.14.0': "implementation 'org.opencv:opencv:4.14.0'" in gradle,
    'vision pipeline class': Path('app/src/main/java/com/fantest/pokervision/VisionPipeline.java').exists(),
    'suit-first picker class': Path('app/src/main/java/com/fantest/pokervision/CardPickerModel.java').exists(),
    'new v0.3.0 activity': activity_path.exists(),
    'suit-first copy present': 'Choose suit' in activity,
    'old rank-first copy absent': 'Choose rank, then suit.' not in activity,
    'review before scan commit': 'Confirm all' in activity or 'Confirm All' in activity,
    'math permits nine opponents': 'opponents > 9' in math,
    'legacy build-time picker patch absent': 'apply_v028_card_tap_picker.py' not in workflow,
    'legacy build-time availability patch absent': 'apply_v029_card_availability.py' not in workflow,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit('v0.3.0 source verification failed:\n- ' + '\n- '.join(failed))

print('Poker Vision v0.3.0 source contract is self-contained and ready for packaging.')
