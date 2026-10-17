#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 3DS CIA 自定义软件里提取内置频道目录，生成 Persephone TV 可使用的 M3U。

注意：产物是「第三方频道地址清单」，不是内容本身，也不代表本项目提供这些频道。

用法:
    python scripts/extract-cia-sources.py 魔方电视-v1.2.9.cia
    python scripts/extract-cia-sources.py xxx.cia --all -o out/all.m3u
    python scripts/extract-cia-sources.py xxx.cia --json out/all.json

CIA 结构（无加密时）:
    header(0x20) | cert(2560) | ticket(848) | tmd(2868) | meta(15040) | content...
    其中 header 里的 cert/ticket/tmd/meta 四个长度字段决定 content 起点。

魔方电视把频道目录以纯文本塞在 content 里（RomFS），格式:
    频道名称|播放地址|分类与画质|国家/地区|Referrer|User-Agent
"""

import argparse
import base64
import json
import os
import struct
import sys

# 中国相关国家/地区标签
CN_TAGS = ("中国",)


def read_header(buf):
    """解析 CIA header，返回 content 起始偏移。

    全部小端: u16 header_size, u16 type, u32 version,
              u32 cert, u32 ticket, u32 tmd, u32 meta, u64 content_size
    """
    if len(buf) < 0x20:
        raise ValueError("文件太小，不是合法 CIA")
    header_size, _type, _ver, cert, ticket, tmd, meta, _size = struct.unpack("<HHIIIIIQ", buf[:0x20])
    start = header_size + cert + ticket + tmd + meta
    return start, dict(header_size=header_size, cert=cert, ticket=ticket, tmd=tmd, meta=meta)


def entropy(data):
    """粗略熵值，用来判断 content 是否被加密（加密后接近 8.0）。"""
    import math
    if not data:
        return 0.0
    counts = [0] * 256
    for b in data:
        counts[b] += 1
    n = len(data)
    return -sum((c / n) * math.log2(c / n) for c in counts if c)


def extract_records(content):
    """
    从 content 里扫出所有 `名称|地址|分类|国家|Referrer|UA` 记录。
    content 里可能有两份互为副本的目录块，这里按 URL 去重。
    """
    text = content.decode("utf-8", errors="ignore")
    records, seen = [], set()
    for raw in text.split("\n"):
        line = raw.strip("\x00 \t\r\n")
        if "|" not in line or "://" not in line:
            continue
        parts = line.split("|")
        if len(parts) < 4:
            continue
        # 后两列可能缺失，统一补齐到 6 列
        while len(parts) < 6:
            parts.append("")
        name, url, cat, country, referrer, ua = [p.strip() for p in parts[:6]]
        if not url.lower().startswith(("http://", "https://")):
            continue
        key = url.lower()
        if key in seen:
            continue
        seen.add(key)
        records.append(dict(name=name or "未命名频道", url=url, cat=cat,
                            country=country, referrer=referrer, ua=ua))
    return records


# ==================== 归类 ====================

# 分类字段里出现的省级行政区
PROVINCES = (
    "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
    "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
    "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
    "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆",
)

# 蜻蜓FM / 央广 等音频站，这些是广播不是电视
RADIO_HOSTS = ("ls.qingting.fm", "satellitepull.cnr.cn", "radio.pull.hebtv.com")

# 源里用英文写的地方台，按名字前缀补上省份（否则全挤进「其他」）
NAME_HINTS = (
    ("Baicheng", "吉林"),
    ("Siping", "吉林"),
    ("Chifeng", "内蒙古"),
    ("Harbin", "黑龙江"),
    ("Hebei TV", "河北"),
    ("Jiangsu Public", "江苏"),
    ("Lanzhou", "甘肃"),
    ("QTV-", "山东"),
    ("Xinjiang TV", "新疆"),
    ("Zhejiang International", "浙江"),
    ("Dragon TV International", "上海"),
)


def hint_province(name):
    for prefix, province in NAME_HINTS:
        if name.startswith(prefix):
            return province
    return None


def is_radio(url):
    return any(h in url for h in RADIO_HOSTS)


def group_of(rec):
    """把源里的原始分类整理成电视端好浏览的分组。"""
    name, url, cat, country = rec["name"], rec["url"], rec["cat"], rec["country"]
    base = cat.split()[0] if cat else ""

    if country in ("中国台湾", "中国香港", "中国澳门"):
        return "港澳台"
    if is_radio(url):
        return "广播电台"
    if name.upper().startswith(("CCTV", "CGTN")) or base == "中央广播电视总台":
        return "央视与 CGTN"
    if "卫视" in name:
        return "卫视"
    province = base if base in PROVINCES else hint_province(name)
    if province:
        return f"地方台 · {province}"
    return "其他"


GROUP_ORDER = ["央视与 CGTN", "卫视", "港澳台", "广播电台", "其他"]


def group_rank(g):
    if g in GROUP_ORDER:
        return GROUP_ORDER.index(g)
    return GROUP_ORDER.index("其他") - 1 if g.startswith("地方台") else len(GROUP_ORDER)


def mirror_score(rec):
    """同名多条镜像时的择优：https 优先，其次地址短的（少带签名参数，通常更稳）。"""
    return (0 if rec["url"].lower().startswith("https://") else 1, len(rec["url"]))


def dedupe_by_name(records):
    """同一个分组里同名的只留一条，避免电视端列表里出现一串 CGTN。"""
    best = {}
    for r in records:
        key = (group_of(r), r["name"].strip().lower())
        cur = best.get(key)
        if cur is None or mirror_score(r) < mirror_score(cur):
            best[key] = r
    keep = {id(v) for v in best.values()}
    return [r for r in records if id(r) in keep]


# ==================== 输出 M3U ====================

# ==================== 打包用的混淆 ====================

# 仓库里不放明文播放地址：原文按字节做凯撒位移再 base64。
# **这是混淆，不是加密** —— 目的只是让爬代码仓库的脚本不能直接 grep 出一堆 .m3u8，
# 认真想还原的人三行代码就还原了。别把它当安全边界，也别在里面放敏感数据。
SHIFT = 13
MAGIC = "# PERS-ENC1"


def encrypt(text: str) -> str:
    shifted = bytes((b + SHIFT) % 256 for b in text.encode("utf-8"))
    b64 = base64.b64encode(shifted).decode("ascii")
    out = [MAGIC]
    out += [b64[i:i + 76] for i in range(0, len(b64), 76)]
    return "\n".join(out) + "\n"


def to_m3u(records, title):
    out = [
        "#EXTM3U",
        f"# {title}",
        "# 频道地址清单，提取自 3DS 自制软件「魔方电视」v1.2.9 的内置目录",
        "# 本项目不提供、不运营这些频道；播放需要联网",
        "# 字段: 频道名称|播放地址|分类与画质|国家/地区|Referrer|User-Agent",
        "",
    ]
    records = sorted(records, key=lambda r: (group_rank(group_of(r)), r["name"]))
    for r in records:
        group = group_of(r)
        lines = [
            f'#EXTINF:-1 tvg-name="{r["name"]}" group-title="{group}",{r["name"]}'
        ]
        if r["referrer"]:
            lines.append(f"#EXTVLCOPT:http-referrer={r['referrer']}")
        if r["ua"]:
            lines.append(f"#EXTVLCOPT:http-user-agent={r['ua']}")
        lines.append(r["url"])
        out.extend(lines)
    out.append("")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser(description="从 CIA 提取其内置频道目录")
    ap.add_argument("cia", help="CIA 文件路径")
    ap.add_argument("-o", "--out", help="输出路径，写 .m3u 出明文、写 .enc 出混淆后内容")
    ap.add_argument("--json", help="额外导出 JSON 路径（明文，只用于本地核对，别提交）")
    ap.add_argument("--all", action="store_true", help="导出全部频道，默认只导中国相关")
    args = ap.parse_args()

    with open(args.cia, "rb") as f:
        buf = f.read()

    start, info = read_header(buf)
    content = buf[start:]
    ent = entropy(content)

    print(f"文件      : {os.path.basename(args.cia)} ({len(buf)} 字节)")
    print(f"content   : 偏移 {start:#x}，{len(content)} 字节")
    print(f"熵        : {ent:.2f} bits/byte" + ("（疑似加密，放弃）" if ent > 7.9 else "（未加密）"))

    records = extract_records(content)
    print(f"提取记录  : {len(records)} 条")
    if not records:
        print("没扫到任何 `名称|地址|...` 记录，这个 CIA 的源可能是在运行时从服务器拉的。")
        return 1

    if not args.all:
        records = [r for r in records if r["country"].startswith(CN_TAGS)]
        print(f"中国相关  : {len(records)} 条")
        before = len(records)
        records = dedupe_by_name(records)
        print(f"同名去重后: {len(records)} 条（去掉 {before - len(records)} 条镜像）")

    if args.json:
        os.makedirs(os.path.dirname(args.json) or ".", exist_ok=True)
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(records, f, ensure_ascii=False, indent=1)
        print(f"JSON      : {args.json}")

    if args.out:
        os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
        title = (
            "Persephone TV · 频道地址清单（提取自 3DS 魔方电视，播放需联网）"
            if not args.all else "Persephone TV · 频道地址清单（全量）"
        )
        text = to_m3u(records, title)
        # .enc 结尾 → 走 base64 + 凯撒混淆后入库，仓库里不放明文
        payload = encrypt(text) if args.out.lower().endswith(".enc") else text
        with open(args.out, "w", encoding="utf-8", newline="\n") as f:
            f.write(payload)
        print(f"{'ENC' if payload is not text else 'M3U'}       : {args.out}（{len(payload)} 字节）")

    # 分组统计
    from collections import Counter
    for g, n in Counter(group_of(r) for r in records).most_common():
        print(f"  {g:<16} {n:>4}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
