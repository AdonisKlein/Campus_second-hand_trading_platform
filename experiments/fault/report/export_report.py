"""Export the Kind fault-isolation canvas as HTML, PDF, and chart PNGs."""

from __future__ import annotations

from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.patches import FancyBboxPatch

OUT = Path(__file__).resolve().parent
LATENCY = [71, 62, 52, 58, 59, 60, 53, 56]
ORDERS = [4, 4, 5]
NAVY = "#1B3A4B"
STEEL = "#3D7EA6"
TEAL = "#2A9D8F"
CORAL = "#C45C4A"
INK = "#1F2A33"
MUTED = "#5C6B73"
BG = "#F7F5F1"
CARD = "#FFFFFF"
LINE = "#D7D2C8"

plt.rcParams.update(
    {
        "font.sans-serif": ["Microsoft YaHei", "SimHei", "Segoe UI", "DejaVu Sans"],
        "axes.unicode_minus": False,
        "figure.facecolor": BG,
        "savefig.facecolor": BG,
        "axes.facecolor": CARD,
        "text.color": INK,
        "axes.labelcolor": INK,
        "xtick.color": INK,
        "ytick.color": INK,
        "axes.edgecolor": LINE,
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    }
)


def new_slide():
    fig = plt.figure(figsize=(13.333, 7.5), dpi=150)
    fig.patch.set_facecolor(BG)
    return fig


def footer(fig, page: str):
    fig.text(0.04, 0.035, "工作项 10 · 成员 C · runId fault-20260902T074227Z-22133ba3", color=MUTED, fontsize=9)
    fig.text(0.96, 0.035, page, color=MUTED, fontsize=9, ha="right")
    fig.add_artist(plt.Line2D([0.04, 0.96], [0.06, 0.06], transform=fig.transFigure, color=LINE, lw=0.8))


def heading(fig, title: str, subtitle: str | None = None):
    fig.text(0.04, 0.93, title, fontsize=22, color=NAVY, fontweight="bold", va="top")
    if subtitle:
        fig.text(0.04, 0.875, subtitle, fontsize=11, color=MUTED, va="top")


def kpi_box(ax, x, y, w, h, value, label, color):
    ax.add_patch(
        FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.012,rounding_size=0.02", linewidth=0, facecolor=CARD, mutation_aspect=0.4)
    )
    ax.text(x + w / 2, y + h * 0.62, value, ha="center", va="center", fontsize=20, color=color, fontweight="bold")
    ax.text(x + w / 2, y + h * 0.28, label, ha="center", va="center", fontsize=10, color=MUTED)


def save_pngs():
    fig, ax = plt.subplots(figsize=(10, 4.4), dpi=160)
    bars = ax.bar([str(i) for i in range(1, 9)], LATENCY, color=CORAL, width=0.62, zorder=3)
    ax.axhline(71, color=NAVY, ls="--", lw=1, label="最大 71 ms")
    ax.set_title("故障阶段 8 次新购买意向延迟", loc="left", color=NAVY, fontsize=14, pad=12)
    ax.set_xlabel("第 N 次 POST /api/orders")
    ax.set_ylabel("Gateway 往返延迟（毫秒）")
    ax.set_ylim(0, 80)
    ax.legend(frameon=False)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    for bar, val in zip(bars, LATENCY):
        ax.text(bar.get_x() + bar.get_width() / 2, val + 1.6, f"{val}", ha="center", va="bottom", fontsize=9, color=INK)
    fig.tight_layout()
    fig.savefig(OUT / "chart-latency.png", bbox_inches="tight")
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(7.2, 4.2), dpi=160)
    bars = ax.bar(["故障前", "故障中", "恢复后"], ORDERS, color=[STEEL, STEEL, TEAL], width=0.5, zorder=3)
    ax.set_title("Trading 库订单行数", loc="left", color=NAVY, fontsize=14, pad=12)
    ax.set_ylabel("订单行数")
    ax.set_ylim(0, 6)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    for bar, val in zip(bars, ORDERS):
        ax.text(bar.get_x() + bar.get_width() / 2, val + 0.12, str(val), ha="center", va="bottom", fontsize=12, color=INK)
    fig.tight_layout()
    fig.savefig(OUT / "chart-orders.png", bbox_inches="tight")
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(10.4, 4.2), dpi=160)
    xs = [0, 1, 2, 3]
    ys = [0, 2, 1, 0]
    ax.step(xs, ys, where="post", color=STEEL, lw=2.4)
    ax.plot(xs, ys, "o", color=NAVY, ms=8)
    ax.set_yticks([0, 1, 2], ["CLOSED", "HALF_OPEN", "OPEN"])
    ax.set_xticks(xs, ["15:42:38\n预置订单", "15:42:41\n熔断打开", "15:42:56\n自动半开", "15:43:19\n闭合恢复"])
    ax.set_title("本 run 熔断状态（北京时间）", loc="left", color=NAVY, fontsize=14, pad=12)
    ax.set_ylim(-0.35, 2.45)
    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.grid(axis="y", color=LINE, lw=0.7)
    fig.tight_layout()
    fig.savefig(OUT / "chart-circuit.png", bbox_inches="tight")
    plt.close(fig)


def draw_table(ax, headers, rows, col_w=None):
    ax.axis("off")
    table = ax.table(cellText=rows, colLabels=headers, loc="upper center", cellLoc="left")
    table.auto_set_font_size(False)
    table.set_fontsize(9.5)
    table.scale(1, 1.55)
    for (r, c), cell in table.get_celld().items():
        cell.set_edgecolor(LINE)
        cell.set_linewidth(0.4)
        if r == 0:
            cell.set_facecolor(NAVY)
            cell.set_text_props(color="white", weight="bold")
        elif r % 2 == 0:
            cell.set_facecolor("#EEF3F6")
        else:
            cell.set_facecolor(CARD)
        if col_w and c < len(col_w):
            cell.set_width(col_w[c])


def build_pdf():
    pdf_path = OUT / "fault-isolation-experiment-report.pdf"
    with PdfPages(pdf_path) as pdf:
        fig = new_slide()
        ax = fig.add_axes([0, 0, 1, 1])
        ax.axis("off")
        fig.text(0.04, 0.82, "校园二手交易平台 · 云原生实验", color=MUTED, fontsize=13)
        fig.text(0.04, 0.70, "Trading → Marketplace", color=NAVY, fontsize=32, fontweight="bold")
        fig.text(0.04, 0.62, "依赖故障隔离实验报告", color=NAVY, fontsize=32, fontweight="bold")
        fig.text(
            0.04,
            0.50,
            "Marketplace 停止后，Trading 稳定返回 503，不写订单、不拖垮其他服务；\n恢复后熔断自行闭合，无需重启 Trading。",
            color=INK,
            fontsize=14,
        )
        kpi_box(ax, 0.04, 0.22, 0.21, 0.18, "PASS", "公共入口判定", TEAL)
        kpi_box(ax, 0.27, 0.22, 0.21, 0.18, "8 / 8", "故障请求返回 503", CORAL)
        kpi_box(ax, 0.50, 0.22, 0.21, 0.18, "71 ms", "故障请求最大延迟", STEEL)
        kpi_box(ax, 0.73, 0.22, 0.21, 0.18, "未重启", "Trading Pod", TEAL)
        footer(fig, "1 / 8")
        pdf.savefig(fig)
        plt.close(fig)

        fig = new_slide()
        heading(fig, "实验环境", "Source: environment.json · 2026-09-02 15:42:29 北京时间")
        ax = fig.add_axes([0.04, 0.10, 0.92, 0.74])
        draw_table(
            ax,
            ["项", "实测值"],
            [
                ["主机", "Windows 11 · AMD Ryzen 9 7940H · 16 逻辑处理器 · 16 GB RAM"],
                ["Docker Desktop", "29.3.1 · 16 CPU · 约 7.4 GB 分配给引擎"],
                ["Kind", "v0.33.0 go1.26.7 windows/amd64"],
                ["Kubernetes", "v1.37.0 · 单节点 campus-ci-control-plane"],
                ["节点 OS / 运行时", "Debian 13 (trixie) · containerd 2.3.4"],
                ["JDK / Maven", "OpenJDK 25.0.4.1 LTS · Maven 3.9.16"],
                ["Git 提交", "22133ba34e08d02d26d1055722cb28f83e8a1208"],
                ["分支", "codex/cloud-native-experiments"],
                ["不可移动基线", "midterm-check 89bd7d68 · microservices-end a21e14fa"],
                ["集群 context / ns", "kind-campus-ci / campus-market"],
            ],
            col_w=[0.22, 0.78],
        )
        footer(fig, "2 / 8")
        pdf.savefig(fig)
        plt.close(fig)

        fig = new_slide()
        heading(fig, "方法", "experiments/run.ps1 -Experiment fault")
        ax = fig.add_axes([0.04, 0.10, 0.92, 0.74])
        draw_table(
            ax,
            ["步骤", "动作", "验收点"],
            [
                ["1", "写入买卖双方、商品、已有购买意向", "Trading 库有既有订单"],
                ["2", "kubectl scale marketplace-service --replicas=0", "依赖不可达"],
                ["3", "连续 8 次 POST /api/orders", "稳定 503，无半条订单"],
                ["4", "GET 既有订单；看 Account/Governance/Trading", "读成功且 Ready"],
                ["5", "scale 回 1，等待 15s 半开探测", "HALF_OPEN → CLOSED"],
                ["6", "再创建购买意向", "新订单写入，Trading 未重启"],
            ],
            col_w=[0.10, 0.52, 0.38],
        )
        footer(fig, "3 / 8")
        pdf.savefig(fig)
        plt.close(fig)

        fig = new_slide()
        heading(fig, "故障响应：固定 503，延迟有上限", "8 次全部 HTTP 503 / PRODUCT_SERVICE_UNAVAILABLE / Retry-After: 1")
        ax = fig.add_axes([0.05, 0.14, 0.90, 0.68])
        bars = ax.bar([str(i) for i in range(1, 9)], LATENCY, color=CORAL, width=0.62, zorder=3)
        ax.axhline(71, color=NAVY, ls="--", lw=1.1, label="最大 71 ms")
        ax.set_xlabel("第 N 次 POST /api/orders")
        ax.set_ylabel("Gateway 往返延迟（毫秒）")
        ax.set_ylim(0, 84)
        ax.legend(frameon=False)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
        for bar, val in zip(bars, LATENCY):
            ax.text(bar.get_x() + bar.get_width() / 2, val + 1.5, f"{val} ms", ha="center", fontsize=9)
        footer(fig, "4 / 8")
        pdf.savefig(fig)
        plt.close(fig)

        fig = new_slide()
        heading(fig, "隔离：依赖挂了，交易库和其他服务不跟着垮", "orderCount 4 → 4 → 5 · tradingRestarted=false")
        ax = fig.add_axes([0.06, 0.16, 0.42, 0.64])
        bars = ax.bar(["故障前", "故障中", "恢复后"], ORDERS, color=[STEEL, STEEL, TEAL], width=0.46, zorder=3)
        ax.set_ylabel("Trading 库订单行数")
        ax.set_ylim(0, 6.2)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
        for bar, val in zip(bars, ORDERS):
            ax.text(bar.get_x() + bar.get_width() / 2, val + 0.12, str(val), ha="center", fontsize=13)
        ax2 = fig.add_axes([0.54, 0.18, 0.40, 0.62])
        draw_table(
            ax2,
            ["断言", "结果"],
            [
                ["otherServicesReady", "true"],
                ["tradingRestarted", "false"],
                ["既有订单可读", "existingOrderId=4"],
                ["恢复后新订单", "recoveredOrderId=5"],
                ["Trading 探针", "liveness/readiness 持续 200"],
            ],
            col_w=[0.48, 0.52],
        )
        footer(fig, "5 / 8")
        pdf.savefig(fig)
        plt.close(fig)

        fig = new_slide()
        heading(fig, "熔断轨迹（本 run，北京时间）", "请用本页，不要用 circuit-transitions.txt 的两轮叠字")
        ax = fig.add_axes([0.08, 0.42, 0.84, 0.40])
        xs = [0, 1, 2, 3]
        ys = [0, 2, 1, 0]
        ax.step(xs, ys, where="post", color=STEEL, lw=2.6)
        ax.plot(xs, ys, "o", color=NAVY, ms=9)
        ax.set_yticks([0, 1, 2], ["CLOSED", "HALF_OPEN", "OPEN"])
        ax.set_xticks(xs, ["15:42:38 预置", "15:42:41 打开", "15:42:56 半开", "15:43:19 闭合"])
        ax.set_ylim(-0.4, 2.5)
        ax.spines["top"].set_visible(False)
        ax.spines["right"].set_visible(False)
        ax.grid(axis="y", color=LINE, lw=0.7)
        ax2 = fig.add_axes([0.04, 0.08, 0.92, 0.30])
        draw_table(
            ax2,
            ["北京时间", "from → to", "要点"],
            [
                ["15:42:41", "CLOSED → OPEN", "failureRate=80.0  bufferedCalls=5  failedCalls=4"],
                ["15:42:56", "OPEN → HALF_OPEN", "打开约 15s 后自动半开"],
                ["15:43:19", "HALF_OPEN → CLOSED", "探测成功，新订单 id=5，无需重启 Trading"],
            ],
            col_w=[0.16, 0.28, 0.56],
        )
        footer(fig, "6 / 8")
        pdf.savefig(fig)
        plt.close(fig)

        fig = new_slide()
        heading(fig, "对照工作项 10 验收", "计划要求与现场证据一一对应")
        ax = fig.add_axes([0.04, 0.10, 0.92, 0.74])
        draw_table(
            ax,
            ["计划验收项", "证据"],
            [
                ["其他服务 Pod 保持 Ready", "otherServicesReady=true；Restarts=0"],
                ["新购买意向无数据库副作用", "订单数故障前/中均为 4"],
                ["有上限的设计 503", "8/8 契约命中，最大 71ms"],
                ["日志四态完整", "CLOSED → OPEN → HALF_OPEN → CLOSED"],
                ["恢复不重启 Trading", "tradingRestarted=false"],
            ],
            col_w=[0.42, 0.58],
        )
        footer(fig, "7 / 8")
        pdf.savefig(fig)
        plt.close(fig)

        fig = new_slide()
        heading(fig, "证据可追溯", "原始目录 Git 忽略，本导出不含 Secret")
        ax = fig.add_axes([0.04, 0.10, 0.92, 0.74])
        draw_table(
            ax,
            ["文件", "用途"],
            [
                ["result.json", "公共入口 status=PASS"],
                ["fault-summary.json", "503 样本与订单计数"],
                ["logs/trading-circuit.log", "熔断状态变化原文"],
                ["cluster-resources.txt", "恢复后 Pod Ready、Restarts=0"],
                ["environment.json", "机器、Kind、K8s、Git SHA"],
                ["复现命令", "experiments/run.ps1 -Experiment fault"],
            ],
            col_w=[0.34, 0.66],
        )
        footer(fig, "8 / 8")
        pdf.savefig(fig)
        plt.close(fig)
    return pdf_path


def svg_bars(values, labels, width, height, max_y, color, ylabel, title, unit=""):
    pad_l, pad_r, pad_t, pad_b = 56, 16, 36, 42
    plot_w = width - pad_l - pad_r
    plot_h = height - pad_t - pad_b
    n = len(values)
    gap = plot_w / n
    bar_w = gap * 0.55
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{title}">',
        f'<rect width="{width}" height="{height}" fill="#ffffff" rx="8"/>',
        f'<text x="{pad_l}" y="22" fill="{NAVY}" font-size="15" font-weight="700" font-family="Segoe UI, Microsoft YaHei, sans-serif">{title}</text>',
        f'<text x="18" y="{pad_t + plot_h / 2}" fill="{MUTED}" font-size="11" font-family="Segoe UI, Microsoft YaHei, sans-serif" transform="rotate(-90 18 {pad_t + plot_h / 2})">{ylabel}</text>',
    ]
    for i in range(0, 5):
        yv = max_y * i / 4
        y = pad_t + plot_h - (yv / max_y) * plot_h
        parts.append(f'<line x1="{pad_l}" y1="{y:.1f}" x2="{width - pad_r}" y2="{y:.1f}" stroke="{LINE}" stroke-width="1"/>')
        parts.append(
            f'<text x="{pad_l - 8}" y="{y + 4:.1f}" text-anchor="end" fill="{MUTED}" font-size="11" font-family="Segoe UI, sans-serif">{int(yv)}</text>'
        )
    for i, (lab, val) in enumerate(zip(labels, values)):
        x = pad_l + gap * i + (gap - bar_w) / 2
        h = (val / max_y) * plot_h
        y = pad_t + plot_h - h
        parts.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{bar_w:.1f}" height="{h:.1f}" fill="{color}" rx="3"/>')
        parts.append(
            f'<text x="{x + bar_w / 2:.1f}" y="{y - 6:.1f}" text-anchor="middle" fill="{INK}" font-size="12" font-family="Segoe UI, sans-serif">{val}{unit}</text>'
        )
        parts.append(
            f'<text x="{x + bar_w / 2:.1f}" y="{height - 14}" text-anchor="middle" fill="{MUTED}" font-size="12" font-family="Segoe UI, Microsoft YaHei, sans-serif">{lab}</text>'
        )
    parts.append("</svg>")
    return "\n".join(parts)


def svg_circuit(width=920, height=280):
    labels = ["15:42:38 预置", "15:42:41 打开", "15:42:56 半开", "15:43:19 闭合"]
    states = [0, 2, 1, 0]
    pad_l, pad_r, pad_t, pad_b = 110, 24, 40, 48
    plot_w = width - pad_l - pad_r
    plot_h = height - pad_t - pad_b
    names = {0: "CLOSED", 1: "HALF_OPEN", 2: "OPEN"}
    def xy(i, s):
        x = pad_l + plot_w * i / 3
        y = pad_t + plot_h - s / 2 * plot_h
        return x, y
    pts = [xy(i, s) for i, s in enumerate(states)]
    lines = []
    for i in range(3):
        x1, y1 = pts[i]
        x2, y2 = pts[i + 1]
        lines.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y1:.1f}" stroke="{STEEL}" stroke-width="3"/>')
        lines.append(f'<line x1="{x2:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="{STEEL}" stroke-width="3"/>')
    circles = "".join(
        f'<circle cx="{x:.1f}" cy="{y:.1f}" r="7" fill="{NAVY}"/>' for x, y in pts
    )
    yticks = "".join(
        f'<text x="{pad_l - 12}" y="{pad_t + plot_h - s / 2 * plot_h + 4:.1f}" text-anchor="end" fill="{MUTED}" font-size="12" font-family="Segoe UI, sans-serif">{names[s]}</text>'
        f'<line x1="{pad_l}" y1="{pad_t + plot_h - s / 2 * plot_h:.1f}" x2="{width - pad_r}" y2="{pad_t + plot_h - s / 2 * plot_h:.1f}" stroke="{LINE}"/>'
        for s in (0, 1, 2)
    )
    xticks = "".join(
        f'<text x="{x:.1f}" y="{height - 14}" text-anchor="middle" fill="{MUTED}" font-size="12" font-family="Segoe UI, Microsoft YaHei, sans-serif">{lab}</text>'
        for (x, _), lab in zip(pts, labels)
    )
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="熔断状态时间线">
  <rect width="{width}" height="{height}" fill="#ffffff" rx="8"/>
  <text x="{pad_l}" y="24" fill="{NAVY}" font-size="15" font-weight="700" font-family="Segoe UI, Microsoft YaHei, sans-serif">本 run 熔断状态（北京时间）</text>
  {yticks}
  {''.join(lines)}
  {circles}
  {xticks}
</svg>'''


def build_html():
    latency_svg = svg_bars(LATENCY, [str(i) for i in range(1, 9)], 920, 320, 80, CORAL, "延迟（毫秒）", "故障阶段 8 次新购买意向延迟", " ms")
    order_svg = svg_bars(ORDERS, ["故障前", "故障中", "恢复后"], 520, 300, 6, STEEL, "订单行数", "Trading 库订单行数")
    circuit_svg = svg_circuit()
    latency_rows = "\n".join(
        f"<tr><td>{i}</td><td>503</td><td>PRODUCT_SERVICE_UNAVAILABLE</td><td>1</td><td>{ms} ms</td></tr>"
        for i, ms in enumerate(LATENCY, 1)
    )
    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Trading → Marketplace 依赖故障隔离实验报告</title>
  <style>
    :root {{
      --navy: {NAVY}; --steel: {STEEL}; --teal: {TEAL}; --coral: {CORAL};
      --ink: {INK}; --muted: {MUTED}; --bg: {BG}; --card: {CARD}; --line: {LINE};
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0; background: var(--bg); color: var(--ink);
      font: 16px/1.55 "Segoe UI", "Microsoft YaHei", sans-serif;
    }}
    main {{ max-width: 980px; margin: 0 auto; padding: 32px 24px 64px; }}
    h1 {{ color: var(--navy); font-size: 32px; line-height: 1.25; margin: 12px 0 8px; }}
    h2 {{ color: var(--navy); font-size: 22px; margin: 40px 0 10px; }}
    p.sub {{ color: var(--muted); margin-top: 0; }}
    .pills span {{
      display: inline-block; background: #e7eef3; color: var(--navy);
      border-radius: 999px; padding: 4px 10px; font-size: 12px; margin-right: 6px;
    }}
    .kpis {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 20px 0; }}
    .kpi {{ background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 16px; }}
    .kpi strong {{ display: block; font-size: 26px; color: var(--navy); }}
    .kpi span {{ color: var(--muted); font-size: 13px; }}
    .callout {{ background: #e8f6f2; border-left: 4px solid var(--teal); padding: 12px 16px; border-radius: 0 8px 8px 0; }}
    table {{ width: 100%; border-collapse: collapse; background: var(--card); font-size: 14px; }}
    th, td {{ border: 1px solid var(--line); padding: 8px 10px; text-align: left; vertical-align: top; }}
    th {{ background: var(--navy); color: #fff; }}
    tr:nth-child(even) td {{ background: #eef3f6; }}
    .chart {{ background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 8px; margin: 12px 0 8px; overflow-x: auto; }}
    code {{ background: #eef3f6; padding: 1px 5px; border-radius: 4px; font-size: 0.92em; }}
    .caption {{ color: var(--muted); font-size: 12px; margin: 0 0 18px; }}
    @media print {{
      body {{ background: #fff; }}
      h2 {{ page-break-before: always; }}
      h2:first-of-type {{ page-break-before: auto; }}
    }}
  </style>
</head>
<body>
<main>
  <p class="pills"><span>工作项 10</span><span>成员 C · 故障隔离</span><span>Kind campus-ci</span></p>
  <h1>Trading → Marketplace 依赖故障隔离实验报告</h1>
  <p class="sub">正式证据 runId <code>fault-20260902T074227Z-22133ba3</code> · result.json = PASS · 北京时间 2026-09-02 15:42:27–15:43:21</p>
  <div class="kpis">
    <div class="kpi"><strong>PASS</strong><span>公共入口判定</span></div>
    <div class="kpi"><strong>8 / 8</strong><span>故障请求返回 503</span></div>
    <div class="kpi"><strong>71 ms</strong><span>故障请求最大延迟</span></div>
    <div class="kpi"><strong>未重启</strong><span>Trading Pod</span></div>
  </div>
  <div class="callout"><strong>一句话结论。</strong> Marketplace Deployment 扩为 0 后，新购买意向全部在 71ms 内返回设计好的 503，Trading 库订单数保持 4 不变；Account / Governance / Trading 保持 Ready。恢复后熔断 CLOSED → OPEN → HALF_OPEN → CLOSED，新订单 id=5 写入成功，无需重启 Trading。</div>

  <h2>1. 实验环境</h2>
  <p class="caption">Source: environment.json · capturedAt 2026-09-02T07:42:29Z</p>
  <table>
    <tr><th>项</th><th>实测值</th></tr>
    <tr><td>主机</td><td>Windows 11 · AMD Ryzen 9 7940H · 16 逻辑处理器 · 16 GB RAM</td></tr>
    <tr><td>Docker Desktop</td><td>29.3.1 · 16 CPU · 约 7.4 GB 分配给引擎</td></tr>
    <tr><td>Kind</td><td>v0.33.0 go1.26.7 windows/amd64</td></tr>
    <tr><td>Kubernetes</td><td>v1.37.0 · 单节点 campus-ci-control-plane</td></tr>
    <tr><td>节点 OS / 运行时</td><td>Debian 13 (trixie) · containerd 2.3.4</td></tr>
    <tr><td>JDK / Maven</td><td>OpenJDK 25.0.4.1 LTS · Maven 3.9.16</td></tr>
    <tr><td>Git 提交</td><td>22133ba34e08d02d26d1055722cb28f83e8a1208</td></tr>
    <tr><td>分支</td><td>codex/cloud-native-experiments</td></tr>
    <tr><td>不可移动基线</td><td>midterm-check 89bd7d68 · microservices-end a21e14fa</td></tr>
    <tr><td>集群 context / ns</td><td>kind-campus-ci / campus-market</td></tr>
  </table>

  <h2>2. 方法</h2>
  <p>入口 <code>experiments/run.ps1 -Experiment fault</code>。熔断窗口 10、最少 5 次、失败率 50%、打开 15s、半开 2 次；连接超时 300ms、响应超时 800ms、仅 GET 重试一次。</p>
  <table>
    <tr><th>步骤</th><th>动作</th><th>验收点</th></tr>
    <tr><td>1</td><td>写入买卖双方、商品、已有购买意向</td><td>Trading 库有既有订单</td></tr>
    <tr><td>2</td><td>kubectl scale marketplace-service --replicas=0</td><td>依赖不可达</td></tr>
    <tr><td>3</td><td>连续 8 次 POST /api/orders</td><td>稳定 503，无半条订单</td></tr>
    <tr><td>4</td><td>GET 既有订单；看 Account/Governance/Trading</td><td>读成功且 Ready</td></tr>
    <tr><td>5</td><td>scale 回 1，等待 15s 半开探测</td><td>HALF_OPEN → CLOSED</td></tr>
    <tr><td>6</td><td>再创建购买意向</td><td>新订单写入，Trading 未重启</td></tr>
  </table>

  <h2>3. 故障响应：固定 503，延迟有上限</h2>
  <p class="caption">横轴：故障阶段第 N 次 POST /api/orders。纵轴：Gateway 往返延迟（毫秒）。</p>
  <div class="chart">{latency_svg}</div>
  <table>
    <tr><th>调用</th><th>HTTP</th><th>code</th><th>Retry-After</th><th>延迟</th></tr>
    {latency_rows}
  </table>

  <h2>4. 隔离：订单数不变</h2>
  <p class="caption">横轴：实验阶段。纵轴：campus_trading 订单行数。故障中保持 4，恢复后为 5。</p>
  <div class="chart">{order_svg}</div>
  <table>
    <tr><th>断言</th><th>结果</th></tr>
    <tr><td>otherServicesReady</td><td>true</td></tr>
    <tr><td>tradingRestarted</td><td>false</td></tr>
    <tr><td>既有订单可读</td><td>existingOrderId=4</td></tr>
    <tr><td>恢复后新订单</td><td>recoveredOrderId=5</td></tr>
  </table>

  <h2>5. 熔断轨迹（本 run）</h2>
  <p class="caption">Source: logs/trading-circuit.log。不要用 circuit-transitions.txt 的两轮叠字当主图。</p>
  <div class="chart">{circuit_svg}</div>
  <table>
    <tr><th>北京时间</th><th>from → to</th><th>要点</th></tr>
    <tr><td>15:42:41</td><td>CLOSED → OPEN</td><td>failureRate=80.0 bufferedCalls=5 failedCalls=4</td></tr>
    <tr><td>15:42:56</td><td>OPEN → HALF_OPEN</td><td>打开约 15s 后自动半开</td></tr>
    <tr><td>15:43:19</td><td>HALF_OPEN → CLOSED</td><td>探测成功，新订单 id=5</td></tr>
  </table>

  <h2>6. 对照验收与证据</h2>
  <table>
    <tr><th>计划验收项</th><th>证据</th></tr>
    <tr><td>其他服务 Pod 保持 Ready</td><td>otherServicesReady=true；Restarts=0</td></tr>
    <tr><td>新购买意向无数据库副作用</td><td>订单数故障前/中均为 4</td></tr>
    <tr><td>有上限的设计 503</td><td>8/8 契约命中，最大 71ms</td></tr>
    <tr><td>日志四态完整</td><td>CLOSED → OPEN → HALF_OPEN → CLOSED</td></tr>
    <tr><td>恢复不重启 Trading</td><td>tradingRestarted=false</td></tr>
  </table>
</main>
</body>
</html>
"""
    path = OUT / "fault-isolation-experiment-report.html"
    path.write_text(html, encoding="utf-8")
    return path


def build_md():
    path = OUT / "fault-isolation-experiment-report.md"
    path.write_text(
        """# Trading → Marketplace 依赖故障隔离实验报告

> 由 Canvas 导出。图表 PNG 与本文件在同一目录；用支持本地图片的 Markdown 预览即可看到柱状图和时间线。

**判定 PASS** · runId `fault-20260902T074227Z-22133ba3` · 北京时间 2026-09-02 15:42:27–15:43:21

Marketplace 停止后，Trading 稳定返回 503，不写订单、不拖垮其他服务；恢复后熔断自行闭合，无需重启 Trading。

| 指标 | 值 |
| --- | --- |
| 公共入口 | PASS |
| 故障请求 | 8 / 8 返回 503 |
| 最大延迟 | 71 ms |
| Trading Pod | 未重启 |

## 故障阶段延迟

横轴为第 N 次 `POST /api/orders`，纵轴为 Gateway 往返延迟（毫秒）。8 次全部 HTTP 503 / `PRODUCT_SERVICE_UNAVAILABLE` / `Retry-After: 1`。

![故障阶段 8 次新购买意向延迟](chart-latency.png)

数据：71, 62, 52, 58, 59, 60, 53, 56 ms

## 订单数隔离

横轴为实验阶段，纵轴为 Trading 库订单行数。故障中保持 4，恢复后为 5。

![Trading 库订单行数](chart-orders.png)

## 熔断状态

本 run 北京时间轨迹。不要用 `circuit-transitions.txt` 的两轮叠字。

![本 run 熔断状态](chart-circuit.png)

- 15:42:41 CLOSED → OPEN（failureRate=80.0）
- 15:42:56 OPEN → HALF_OPEN（约 15s）
- 15:43:19 HALF_OPEN → CLOSED（新订单 id=5）

## 完整可视化文件

- [HTML（浏览器打开，可打印成 PDF）](fault-isolation-experiment-report.html)
- [PDF 幻灯片](fault-isolation-experiment-report.pdf)
""",
        encoding="utf-8",
    )
    return path


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    save_pngs()
    pdf = build_pdf()
    html = build_html()
    md = build_md()
    print(pdf)
    print(html)
    print(md)


if __name__ == "__main__":
    main()
