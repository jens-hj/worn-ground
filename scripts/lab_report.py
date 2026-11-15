#!/usr/bin/env python3
"""Summarise build/lab/*.json from `./gradlew runLab`: trips until a route is visible / permanent / a path."""
import json
import sys
from collections import defaultdict


def first(series, threshold):
    return next((i + 1 for i, value in enumerate(series) if value >= threshold), None)


def lanes(scenarios, key="centre"):
    groups = defaultdict(list)
    for scenario in scenarios:
        if scenario["type"] == "lane":
            groups[scenario["name"]].append(scenario[key])
    print(f"{'route (' + key + ')':28} {'visible':>8} {'perm50':>7} {'perm90':>7} {'path50':>7} {'path90':>7}   end: grass/dirt/coarse/path %")
    for name, runs in groups.items():
        trips = min(len(run) for run in runs)
        mean = [[sum(run[t][k] for run in runs) / sum(sum(run[t]) for run in runs) for k in range(4)] for t in range(trips)]
        worn = [1 - m[0] for m in mean]
        permanent = [m[2] + m[3] for m in mean]
        path = [m[3] for m in mean]
        end = "/".join(f"{100 * v:.0f}" for v in mean[-1])
        cells = [first(worn, 0.5), first(permanent, 0.5), first(permanent, 0.9), first(path, 0.5), first(path, 0.9)]
        print(f"{name:28} " + " ".join(f"{str(c or '-'):>7}" for c in cells) + f"   {end}  ({trips} trips)")


def pens(scenarios):
    groups = defaultdict(list)
    for scenario in scenarios:
        if scenario["type"] == "pen":
            groups[scenario["name"]].append(scenario["samples"])
    for name, runs in groups.items():
        print(name)
        for day in (0.5, 1, 2, 3, 5, 10, 20):
            index = int(day * 10) - 1
            if index >= min(len(run) for run in runs):
                continue
            total = sum(sum(run[index]) for run in runs)
            print(f"  day {day:>4}: " + "/".join(f"{100 * sum(run[index][k] for run in runs) / total:.0f}" for k in range(4)) + " % grass/dirt/coarse/path")


def vehicles(scenarios):
    for scenario in scenarios:
        if scenario["type"] != "vehicle":
            continue
        # Each census strip is two blocks wide; the tire runs along one of them.
        track = len(scenario["passes"]) and sum(scenario["passes"][0]["left"]) // 2
        series = []
        for sample in scenario["passes"]:
            counts = [sample["left"][k] + sample["right"][k] for k in range(4)]
            series.append([c / (2 * track) for c in counts])
        worn = [min(1, s[1] + s[2] + s[3]) for s in series]
        permanent = [min(1, s[2] + s[3]) for s in series]
        path = [min(1, s[3]) for s in series]
        under = scenario["passes"][-1]["under"] if scenario["passes"] else None
        print(f"{scenario['name']:34} passes={len(series):4} visible={first(worn, 0.5)} perm50={first(permanent, 0.5)} "
              f"perm90={first(permanent, 0.9)} path50={first(path, 0.5)} path90={first(path, 0.9)} under-chassis={under}")


def summary(files, out):
    """Mean block mix per trip / day / pass, small enough to commit and chart."""
    result = {"lanes": {}, "pens": {}, "vehicles": {}}
    for file in files:
        scenarios = json.load(open(file))["scenarios"]
        groups = defaultdict(list)
        for scenario in scenarios:
            if scenario["type"] == "lane":
                groups[scenario["name"]].append(scenario["centre"])
        for name, runs in groups.items():
            trips = min(len(run) for run in runs)
            result["lanes"][name] = [[round(sum(run[t][k] for run in runs) / sum(sum(run[t]) for run in runs), 3) for k in range(4)] for t in range(trips)]
        groups = defaultdict(list)
        for scenario in scenarios:
            if scenario["type"] == "pen":
                groups[scenario["name"]].append(scenario["samples"])
        for name, runs in groups.items():
            count = min(len(run) for run in runs)
            result["pens"][name] = [[round(sum(run[t][k] for run in runs) / sum(sum(run[t]) for run in runs), 3) for k in range(4)] for t in range(count)]
        for scenario in scenarios:
            if scenario["type"] == "vehicle" and scenario["passes"]:
                track = sum(scenario["passes"][0]["left"]) // 2
                rows = []
                for sample in scenario["passes"][:200]:
                    worn = [(sample["left"][k] + sample["right"][k]) / (2 * track) for k in (1, 2, 3)]
                    worn = [min(1.0, value) for value in worn]
                    rows.append([round(max(0.0, 1 - sum(worn)), 3)] + [round(value, 3) for value in worn])
                result["vehicles"][scenario["name"]] = rows
    json.dump(result, open(out, "w"), separators=(",", ":"))


if "--summary" in sys.argv:
    index = sys.argv.index("--summary")
    summary(sys.argv[1:index], sys.argv[index + 1])
    sys.exit()

for file in sys.argv[1:]:
    data = json.load(open(file))
    print(f"== {file} ({data['ticks']} ticks)")
    lanes(data["scenarios"])
    lanes(data["scenarios"], "band")
    pens(data["scenarios"])
    vehicles(data["scenarios"])
