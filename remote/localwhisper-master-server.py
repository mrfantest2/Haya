from __future__ import annotations

import os
import shutil
import tempfile
import threading
import time
from pathlib import Path

CUDA_DLL_DIR = r"C:\\LocalWhisper\\remote-server\\cuda-libs"
if os.name == "nt" and os.path.isdir(CUDA_DLL_DIR):
    os.add_dll_directory(CUDA_DLL_DIR)
    os.environ["PATH"] = CUDA_DLL_DIR + os.pathsep + os.environ.get("PATH", "")

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from faster_whisper import WhisperModel

ROOT = Path(r"C:\LocalWhisper\remote-server")
MODEL_DIR = ROOT / "models"
TMP_DIR = ROOT / "tmp"
LOG_DIR = ROOT / "logs"
MODEL_NAME = os.environ.get("LOCALWHISPER_REMOTE_MODEL", "large-v3-turbo")
MODEL_NAMES = {"quality": MODEL_NAME, "fast": "base"}
DEVICE = os.environ.get("LOCALWHISPER_REMOTE_DEVICE", "cuda")
COMPUTE_TYPE = os.environ.get("LOCALWHISPER_REMOTE_COMPUTE", "int8_float16")
BIND_HOST = os.environ.get("LOCALWHISPER_REMOTE_BIND", "100.68.12.73")
ALLOWED_CLIENTS = {"127.0.0.1", "::1", "100.68.12.73", "100.99.16.99"}

for d in (MODEL_DIR, TMP_DIR, LOG_DIR):
    d.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="LocalWhisper Master PC", version="0.2.5")
_models: dict[str, WhisperModel] = {}
_model_lock = threading.Lock()
_infer_lock = threading.Lock()
_model_error = ""


def _check_client(request: Request) -> None:
    host = request.client.host if request.client else ""
    if host not in ALLOWED_CLIENTS:
        raise HTTPException(status_code=403, detail="Tailnet client not allowed")


def _load_model(model_id: str = "quality") -> WhisperModel:
    global _model_error
    key = model_id if model_id in MODEL_NAMES else "quality"
    if key in _models:
        return _models[key]
    with _model_lock:
        if key in _models:
            return _models[key]
        try:
            model = WhisperModel(
                MODEL_NAMES[key],
                device=DEVICE,
                compute_type=COMPUTE_TYPE,
                download_root=str(MODEL_DIR),
            )
            _models[key] = model
            _model_error = ""
            return model
        except Exception as exc:
            _model_error = f"{type(exc).__name__}: {exc}"
            raise


def _fmt_ts(seconds: float) -> str:
    seconds = max(0, int(seconds))
    return f"{seconds // 60:02d}:{seconds % 60:02d}"


def _profile(profile: str) -> tuple[int, int, bool]:
    p = (profile or "fast").lower()
    if p == "accurate":
        return 5, 5, True
    if p == "balanced":
        return 3, 3, True
    return 1, 1, False


def _run_pass(path: str, language: str, task: str, timestamps: bool, profile: str, model_id: str):
    model = _load_model(model_id)
    beam_size, best_of, condition_on_previous_text = _profile(profile)
    kwargs = dict(
        language=None if language in ("", "auto", None) else language,
        task=task,
        beam_size=beam_size,
        best_of=best_of,
        temperature=0.0,
        vad_filter=True,
        condition_on_previous_text=condition_on_previous_text,
    )
    segments, info = model.transcribe(path, **kwargs)
    chunks = []
    plain = []
    for seg in segments:
        text = (seg.text or "").strip()
        if not text:
            continue
        plain.append(text)
        chunks.append(f"[{_fmt_ts(seg.start)}] {text}" if timestamps else text)
    return "\n".join(chunks).strip(), " ".join(plain).strip(), info


@app.on_event("startup")
def preload_quality_model():
    threading.Thread(target=lambda: _load_model("quality"), daemon=True, name="localwhisper-prewarm").start()


@app.get("/health")
def health(request: Request):
    _check_client(request)
    return {
        "ok": True,
        "service": "LocalWhisper Master PC",
        "version": "0.2.5",
        "model": MODEL_NAME,
        "device": DEVICE,
        "compute_type": COMPUTE_TYPE,
        "loaded_models": list(_models.keys()),
        "model_loaded": "quality" in _models,
        "model_error": _model_error,
    }


@app.post("/v1/warm")
def warm(request: Request):
    _check_client(request)
    started = time.perf_counter()
    try:
        _load_model("quality")
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"Model load failed: {exc}")
    return {"ok": True, "model": MODEL_NAME, "load_ms": round((time.perf_counter() - started) * 1000)}


@app.post("/v1/transcribe")
async def transcribe(
    request: Request,
    file: UploadFile = File(...),
    language: str = Form("auto"),
    output: str = Form("original"),
    timestamps: bool = Form(False),
    performance: str = Form("fast"),
    model_id: str = Form("quality"),
):
    _check_client(request)
    started = time.perf_counter()
    suffix = Path(file.filename or "audio.bin").suffix[:12]
    fd, temp_path = tempfile.mkstemp(prefix="lw_", suffix=suffix, dir=TMP_DIR)
    os.close(fd)
    try:
        with open(temp_path, "wb") as out:
            while True:
                chunk = await file.read(1024 * 1024)
                if not chunk:
                    break
                out.write(chunk)

        if os.path.getsize(temp_path) == 0:
            raise HTTPException(status_code=400, detail="Empty audio file")

        with _infer_lock:
            if output == "english":
                translated, _, info = _run_pass(temp_path, language, "translate", timestamps, performance, model_id)
                result = translated
                detected = getattr(info, "language", "") or ""
                duration = float(getattr(info, "duration", 0.0) or 0.0)
            elif output == "both":
                original, _, info = _run_pass(temp_path, language, "transcribe", timestamps, performance, model_id)
                translated, _, _ = _run_pass(temp_path, language, "translate", timestamps, performance, model_id)
                result = f"Original:\n{original}\n\nEnglish:\n{translated}"
                detected = getattr(info, "language", "") or ""
                duration = float(getattr(info, "duration", 0.0) or 0.0)
            else:
                original, _, info = _run_pass(temp_path, language, "transcribe", timestamps, performance, model_id)
                result = original
                detected = getattr(info, "language", "") or ""
                duration = float(getattr(info, "duration", 0.0) or 0.0)

        if not result.strip():
            raise HTTPException(status_code=422, detail="Whisper returned no text")

        elapsed_ms = round((time.perf_counter() - started) * 1000)
        return {
            "ok": True,
            "text": result.strip(),
            "language": detected,
            "duration_ms": round(duration * 1000),
            "processing_ms": elapsed_ms,
            "engine": "master-pc-rtx2060",
            "model": MODEL_NAMES.get(model_id, MODEL_NAME),
            "model_id": model_id if model_id in MODEL_NAMES else "quality",
            "compute_type": COMPUTE_TYPE,
            "performance": performance,
            "client": request.headers.get("tailscale-user-login", "tailnet"),
        }
    finally:
        try:
            os.remove(temp_path)
        except OSError:
            pass


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=BIND_HOST, port=8766, log_level="info")
