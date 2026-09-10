#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AKShare 轻量网关：为 KMP 客户端暴露 3 个 REST 端点。

  GET /breadth            市场广度（涨/跌/涨停/跌停/停牌/活跃度）
  GET /ladder             连板梯队（涨停池按连板数分层）
  GET /flow?code=600519   个股资金流向（主力/超大单/大单/中单/小单）

运行：
  pip install akshare fastapi uvicorn
  python scripts/ak_gateway.py            # 监听 127.0.0.1:8790

设计说明：
- akshare 单次调用秒级，内置 TTL 内存缓存避免穿透；
- 涨停池日期自动回退到最近有数据的交易日（盘中/盘后均可）；
- 失败统一返回 {"error": "..."}，KMP 侧按字段缺失降级。
"""
from __future__ import annotations

import datetime as dt
import threading
import time
from typing import Any

import akshare as ak
from fastapi import FastAPI, Query
from fastapi.responses import JSONResponse

app = FastAPI(title="ak-gateway")

_TTL_BREADTH = 60          # 广度盘中实时，1 分钟
_TTL_LADDER = 300          # 涨停池 5 分钟
_TTL_FLOW = 600            # 资金流 10 分钟
_cache: dict[str, tuple[float, Any]] = {}
_lock = threading.Lock()


def _cached(key: str, ttl: int, loader):
    with _lock:
        hit = _cache.get(key)
        if hit and time.time() - hit[0] < ttl:
            return hit[1]
    value = loader()
    with _lock:
        _cache[key] = (time.time(), value)
    return value


def _num(v: Any) -> float:
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


# region 市场广度

def _load_breadth() -> dict:
    df = ak.stock_market_activity_legu()
    items = {str(r["item"]).strip(): r["value"] for _, r in df.iterrows()}
    activity_raw = str(items.get("活跃度", "0")).replace("%", "")
    return {
        "date": str(items.get("统计日期", "")),
        "advancing": int(_num(items.get("上涨"))),
        "declining": int(_num(items.get("下跌"))),
        "limitUp": int(_num(items.get("涨停"))),
        "limitDown": int(_num(items.get("跌停"))),
        "halted": int(_num(items.get("停牌"))),
        "activity": _num(activity_raw),  # 百分比数值，如 25.95
    }


@app.get("/breadth")
def breadth():
    try:
        return _cached("breadth", _TTL_BREADTH, _load_breadth)
    except Exception as e:  # noqa: BLE001
        return JSONResponse({"error": f"{type(e).__name__}: {e}"}, status_code=502)


# endregion

# region 连板梯队

def _zt_pool_with_fallback(max_lookback: int = 6):
    """从最近日期开始回退，找到第一个非空的涨停池。返回 (df, date_str)。"""
    today = dt.date.today()
    for i in range(max_lookback):
        d = today - dt.timedelta(days=i)
        if d.weekday() >= 5:  # 跳过周末
            continue
        ds = d.strftime("%Y%m%d")
        try:
            df = ak.stock_zt_pool_em(date=ds)
        except Exception:  # noqa: BLE001
            continue
        if df is not None and len(df) > 0:
            return df, d.strftime("%Y-%m-%d")
    return None, None


def _load_ladder() -> dict:
    df, date_str = _zt_pool_with_fallback()
    if df is None:
        return {"date": "", "levels": []}
    # 只保留真实连板（>=2）与首板分开两层展示
    grouped: dict[int, list] = {}
    for _, r in df.iterrows():
        boards = int(_num(r.get("连板数", 1)))
        boards = max(boards, 1)
        cap = _num(r.get("流通市值"))
        grouped.setdefault(boards, []).append({
            "name": str(r.get("名称", "")),
            "code": str(r.get("代码", "")),
            "changePct": round(_num(r.get("涨跌幅")), 2),
            "marketCap": cap,  # 元
        })
    levels = [
        {"level": lv, "stocks": sorted(sts, key=lambda s: -s["marketCap"])[:8]}
        for lv, sts in sorted(grouped.items(), reverse=True)
        if lv >= 2
    ]
    first_board = grouped.get(1, [])
    if first_board:
        levels.append({"level": 1, "stocks": sorted(first_board, key=lambda s: -s["marketCap"])[:8]})
    return {"date": date_str, "levels": levels}


@app.get("/ladder")
def ladder():
    try:
        return _cached("ladder", _TTL_LADDER, _load_ladder)
    except Exception as e:  # noqa: BLE001
        return JSONResponse({"error": f"{type(e).__name__}: {e}"}, status_code=502)


# endregion

# region 个股资金流向

def _load_flow(code: str) -> dict:
    market = "sh" if code.startswith(("6", "9")) else "sz"
    df = ak.stock_individual_fund_flow(stock=code, market=market)
    if df is None or len(df) == 0:
        return {"code": code, "date": "", "flows": []}
    row = df.iloc[-1]

    def g(prefix: str, suffix: str) -> float:
        return _num(row.get(f"{prefix}净流入-{suffix}"))

    flows = []
    for label, prefix in [("主力", "主力"), ("超大单", "超大单"), ("大单", "大单"),
                          ("中单", "中单"), ("小单", "小单")]:
        flows.append({
            "label": label,
            "netInflow": g(prefix, "净额"),        # 元
            "pct": g(prefix, "净占比"),            # 百分比数值
        })
    return {"code": code, "date": str(row.get("日期", "")), "flows": flows}


@app.get("/flow")
def flow(code: str = Query(..., min_length=6, max_length=6, pattern=r"^\d{6}$")):
    try:
        return _cached(f"flow:{code}", _TTL_FLOW, lambda: _load_flow(code))
    except Exception as e:  # noqa: BLE001
        return JSONResponse({"error": f"{type(e).__name__}: {e}"}, status_code=502)


# endregion


@app.get("/health")
def health():
    return {"ok": True, "akshare": ak.__version__}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8790, log_level="warning")
