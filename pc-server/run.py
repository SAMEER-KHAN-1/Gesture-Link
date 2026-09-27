"""Entry point: `python run.py` starts the GestureLink server on this PC."""

import uvicorn

from server.config import load_config

if __name__ == "__main__":
    config = load_config()
    uvicorn.run("server.main:app", host=config["host"], port=config["port"], reload=False)
