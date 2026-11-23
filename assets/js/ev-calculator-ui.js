/* EMIFORMULA — EV vs Petrol/Diesel calculator UI (all languages; strings come from window.EVC_I18N) */
(function () {
  "use strict";
  var E = window.EMIFORMULA_EV, R = window.EVC_I18N;
  if (!E || !R) return;

  var doc = document;
  function $(s, c) { return (c || doc).querySelector(s); }
  function $$(s, c) { return Array.prototype.slice.call((c || doc).querySelectorAll(s)); }

  /* ---------- currency (English page only gets the selector; others stay INR) ---------- */
  var CUR = {
    INR: { sym: "₹", loc: "en-IN" }, USD: { sym: "$", loc: "en-US" }, EUR: { sym: "€", loc: "en-IE" },
    GBP: { sym: "£", loc: "en-GB" }, CAD: { sym: "CA$", loc: "en-CA" }, AUD: { sym: "A$", loc: "en-AU" }, JPY: { sym: "¥", loc: "ja-JP" }
  };
  var isEn = (doc.documentElement.lang || "en").toLowerCase().indexOf("en") === 0;
  var state = { cur: isEn && CUR[window.__EF_CURRENCY] ? window.__EF_CURRENCY : "INR", last1: null, last2: null };

  function grp(n, dec) {
    var c = CUR[state.cur];
    return Number(n).toLocaleString(c.loc, { minimumFractionDigits: dec || 0, maximumFractionDigits: dec || 0 });
  }
  function money(n, dec) {
    if (!isFinite(n)) return "–";
    var neg = n < 0 && Math.abs(n) >= (dec ? 0.005 : 0.5);
    return (neg ? "−" : "") + CUR[state.cur].sym + grp(Math.abs(n), dec || 0);
  }
  function kmText(n) { return grp(Math.round(n / (n >= 2000 ? 100 : 10)) * (n >= 2000 ? 100 : 10)) + " " + R.km; }
  function fill(tpl, map) { return tpl.replace(/\{(\w+)\}/g, function (m, k) { return map[k] === undefined ? m : map[k]; }); }

  function dur(months) {
    var s = E.splitMonths(months), p = [];
    if (s.years > 0) p.push(s.years + " " + (s.years === 1 ? R.dur.y1 : R.dur.yN));
    if (s.months > 0 || s.years === 0) p.push(s.months + " " + (s.months === 1 ? R.dur.m1 : R.dur.mN));
    return p.join(" ");
  }
  function recText(r, withKm) {
    if (r.status === "immediate") return R.immediate;
    if (r.status === "never") return R.never;
    if (r.status === "veryLong") return R.veryLong;
    return dur(r.months) + (withKm ? " (" + kmText(r.km) + ")" : "");
  }

  /* ---------- inputs ---------- */
  function readVal(el) {
    if (el.tagName === "SELECT") return el.value;
    var t = String(el.value).replace(/,/g, "").trim();
    return t === "" ? NaN : Number(t);
  }
  function collect(panel) {
    var o = {};
    $$("[data-k]", panel).forEach(function (el) { o[el.getAttribute("data-k")] = readVal(el); });
    return o;
  }
  function markInvalid(panel, keys) {
    $$("[data-k]", panel).forEach(function (el) { el.classList.toggle("is-invalid", keys.indexOf(el.getAttribute("data-k")) > -1); });
  }
  function fmtField(el) {
    if (!el.hasAttribute("data-money") || el.value.trim() === "") return;
    var n = Number(el.value.replace(/,/g, ""));
    if (isFinite(n)) el.value = n.toLocaleString(CUR[state.cur].loc, { maximumFractionDigits: 2 });
  }
  function setText(id, v) { var el = doc.getElementById(id); if (el) el.textContent = v; }
  function setHtml(id, v) { var el = doc.getElementById(id); if (el) el.innerHTML = v; }
  function rows(id, list) {
    var tb = doc.getElementById(id); if (!tb) return;
    tb.innerHTML = list.map(function (r) {
      return "<tr" + (r.cls ? ' class="' + r.cls + '"' : "") + ">" + r.cells.map(function (c, i) {
        return (i === 0 ? "<th scope=\"row\">" : "<td>") + c + (i === 0 ? "</th>" : "</td>");
      }).join("") + "</tr>";
    }).join("");
  }

  /* ---------- Tab 1 ---------- */
  function calc1() {
    var panel = $("#panel-loan"), msg = $("#t1-msg"), res = $("#t1-results");
    var v = collect(panel);
    var r = E.exchangeLoan({ oldResale: v.oldResale, pendingLoan: v.pendingLoan, onRoadPrice: v.onRoadPrice, cashDown: v.cashDown, annualRate: v.annualRate, tenureYears: v.tenureYears });
    if (!r.ok) {
      markInvalid(panel, r.errors); msg.textContent = R.errorGeneric; msg.hidden = false; res.classList.add("is-stale"); return;
    }
    markInvalid(panel, []); msg.hidden = true; res.classList.remove("is-stale"); state.last1 = r;

    setText("t1-v-loan", money(r.netLoan)); setText("t1-v-emi", money(r.emi)); setText("t1-v-true", money(r.trueCost));
    setText("k1-emi", money(r.emi)); setText("k1-loan", money(r.netLoan)); setText("k1-down", money(r.effectiveDown));
    setText("k1-interest", money(r.totalInterest)); setText("k1-payable", money(r.totalPayable)); setText("k1-true", money(r.trueCost));
    setText("k1-down-sub", money(r.equity) + " + " + money(r.inputs.cash));
    setText("k1-emi-sub", r.months + " " + R.dur.mN);

    var notes = [];
    if (r.shortfall > 0) notes.push(fill(R.negEquity, { x: money(r.shortfall) }));
    if (r.netLoan === 0) notes.push(R.zeroLoan);
    else if (r.loanShare > 0.9) notes.push(fill(R.highLtv, { p: Math.round(r.loanShare * 100) }));
    var fuel = v.fuelType; if (R.tips[fuel]) notes.push(R.tips[fuel]);
    setHtml("t1-notes", notes.map(function (n) { return "<li>" + n + "</li>"; }).join(""));

    var b = R.t1rows;
    rows("t1-breakdown", [
      { cells: [b.onRoad, money(r.inputs.onRoad)] },
      { cells: [b.resale, "− " + money(r.inputs.resale)] },
      { cells: [b.pending, "+ " + money(r.inputs.pending)] },
      { cells: [b.cash, "− " + money(r.inputs.cash)] },
      { cls: "is-key", cells: [b.loan, money(r.netLoan)] },
      { cells: [b.interest, "+ " + money(r.totalInterest)] },
      { cls: "is-key", cells: [b.trueCost, money(r.trueCost)] },
      { cells: [b.cashOut, money(r.cashOutOfPocket)] }
    ]);
    rows("t1-amort", r.schedule.map(function (y) {
      return { cells: [String(y.year), money(y.opening), money(y.principal), money(y.interest), money(y.payment), money(y.closing)] };
    }));
  }

  /* ---------- Tab 2 ---------- */
  function updatePill() {
    var v = collect($("#panel-running"));
    var ch = E.chargeCost({ batteryKwh: v.batteryKwh, chargingLossPct: v.chargingLossPct, publicSharePct: v.publicSharePct, electricityPrice: v.electricityPrice, publicPrice: v.publicPrice });
    var el = $("#t2-pill"); if (!el) return;
    if (!isFinite(ch.cost) || !(v.batteryKwh > 0)) { el.textContent = "–"; return; }
    el.innerHTML = fill(R.pill, { batt: grp(v.batteryKwh, 0), units: "<strong>" + grp(ch.gridUnits, 1) + "</strong>", cost: "<strong>" + money(ch.cost) + "</strong>", loss: grp(ch.loss * 100, 0) });
  }

  /* which cars are compared with the EV (EV is always on) */
  var VEH = ["diesel", "petrol", "hybrid"];
  state.sel = { diesel: true, petrol: true, hybrid: false };
  function selList() { return VEH.filter(function (k) { return state.sel[k]; }); }
  function joinAnd(list) {
    if (list.length <= 1) return list.join("");
    if (list.length === 2) return list[0] + R.andSep + list[1];
    return list.slice(0, -1).join(R.commaSep) + R.andSep + list[list.length - 1];
  }
  function nm(k) { return { n: R.nvs[k], p: R.names[k] }; }
  function strong(s) { return "<strong>" + s + "</strong>"; }
  function setHead(id, cells) {
    var tb = doc.getElementById(id); if (!tb) return;
    var tr = tb.parentNode.querySelector("thead tr");
    tr.innerHTML = cells.map(function (c) { return '<th scope="col">' + c + "</th>"; }).join("");
  }

  function applySelection() {
    VEH.forEach(function (k) {
      $$('[data-sect="' + k + '"]').forEach(function (el) { el.hidden = !state.sel[k]; });
      $$('[data-kveh="' + k + '"]').forEach(function (el) { el.hidden = !state.sel[k]; });
      var chip = $('.evc-chip[data-veh="' + k + '"]');
      if (chip) chip.setAttribute("aria-pressed", state.sel[k] ? "true" : "false");
    });
    var only = selList().length === 1 ? selList()[0] : null;
    $$(".evc-chip[data-veh]").forEach(function (c) {
      if (c.hasAttribute("data-fixed")) return;
      c.setAttribute("aria-disabled", only && c.getAttribute("data-veh") === only ? "true" : "false");
    });
  }
  $$(".evc-chip[data-veh]").forEach(function (c) {
    if (c.hasAttribute("data-fixed")) return;
    c.addEventListener("click", function () {
      var k = c.getAttribute("data-veh");
      if (state.sel[k] && selList().length === 1) return;   // keep at least one car to compare
      state.sel[k] = !state.sel[k]; applySelection(); calc2();
    });
  });

  function calc2() {
    updatePill();
    var panel = $("#panel-running"), msg = $("#t2-msg"), res = $("#t2-results");
    var v = collect(panel); v.compare = selList();
    var r = E.evVsIce(v);
    if (!r.ok) { markInvalid(panel, r.errors); msg.textContent = R.errorGeneric; msg.hidden = false; res.classList.add("is-stale"); return; }
    markInvalid(panel, []); msg.hidden = true; res.classList.remove("is-stale"); state.last2 = r;
    var sel = r.selected, ev = r.cars.ev;

    setHtml("t2-hero", fill(R.hero, {
      xs: sel.map(function (k) { return fill(R.vsA, { v: strong(money(r.vs[k].extraPrice)), n: R.nvs[k], p: R.names[k] }); }).join(" / "),
      rs: joinAnd(sel.map(function (k) { return fill(R.vsB, { v: strong(recText(r.vs[k].extraRecovery, true)), n: R.nvs[k], p: R.names[k] }); })),
      z: strong(money(ev.purchase)),
      fs: sel.map(function (k) { return fill(R.vsB, { v: strong(recText(r.vs[k].fullRecovery, true)), n: R.nvs[k], p: R.names[k] }); }).join(" / ")
    }));
    setHtml("t2-hero-sub", fill(R.heroSub, {
      km: grp(r.monthlyKm),
      items: joinAnd(sel.map(function (k) { return fill(R.subItem, { m: strong(money(r.vs[k].monthlySaving)), y: money(r.vs[k].yearlySaving), n: R.nvs[k], p: R.names[k] }); }))
    }));

    /* decision box */
    var rk = r.ranking5yr, names = R.names;
    function beItem(k) {
      var b = r.breakEven[k], o = nm(k);
      if (b.status === "ok") o.v = "~" + grp(Math.round(b.km / 10) * 10) + " " + R.kmMonth;
      return fill(b.status === "ok" ? R.beItem : (b.status === "always" ? R.beAny : R.beNo), o);
    }
    setHtml("t2-decision", "<strong>" + fill(R.decisionTitle, { name: names[rk[0]] }) + "</strong> " +
      fill(R.decisionBy, { amt: money(r.cheapestMargin), other: names[rk[1]] }) +
      "<br><span>" + fill(R.decisionBe, { items: joinAnd(sel.map(beItem)) }) + "</span>");

    setText("k2-ev", money(ev.perKm.total, 2)); setText("k2-ev-sub", fill(R.elecOnly, { v: money(ev.perKm.energy, 2) }));
    setText("k2-charge", money(r.charge.cost)); setText("k2-charge-sub", fill(R.chargeSub, { u: grp(r.charge.gridUnits, 1) }));
    VEH.forEach(function (k) {
      if (!r.vs[k]) return;
      setText("k2-" + k, money(r.cars[k].perKm.total, 2));
      setText("k2-s" + k + "-m", money(r.vs[k].monthlySaving)); setText("k2-s" + k + "-y5", money(r.vs[k].fiveYearSaving));
    });

    /* per-km bars */
    var all = ["ev"].concat(sel);
    var max = Math.max.apply(null, all.map(function (k) { return r.cars[k].perKm.total; })) || 1;
    setHtml("t2-bars", all.map(function (k) {
      return '<div class="evc-bar"><span class="evc-bar-l">' + names[k] + '</span><span class="evc-bar-t"><i class="evc-bar-f evc-' + k + '" style="width:' +
        Math.max(4, r.cars[k].perKm.total / max * 100).toFixed(1) + '%"></i></span><b>' + money(r.cars[k].perKm.total, 2) + "</b></div>";
    }).join(""));

    /* comparison table */
    var L = R.rows;
    setHead("t2-compare", [R.thMetric].concat(all.map(function (k) { return names[k]; })));
    function rr(label, f, cls) { return { cls: cls, cells: [label].concat(all.map(f)) }; }
    rows("t2-compare", [
      rr(L.purchase, function (k) { return money(r.cars[k].purchase); }),
      rr(L.energy, function (k) { return money(r.cars[k].perKm.energy, 2); }),
      rr(L.service, function (k) { return money(r.cars[k].perKm.service, 2); }),
      rr(L.tyre, function (k) { return money(r.cars[k].perKm.tyre, 2); }),
      rr(L.allin, function (k) { return money(r.cars[k].perKm.total, 2); }, "is-key"),
      rr(L.monthly, function (k) { return money(r.cars[k].monthly); }),
      rr(L.fiveYear, function (k) { return money(r.cars[k].fiveYearTotal); }),
      rr(L.resale, function (k) { return money(r.cars[k].resaleValue); }),
      rr(L.fiveNet, function (k) { return money(r.cars[k].fiveYearNet); }, "is-key"),
      rr(L.extraRec, function (k) { return k === "ev" ? "–" : recText(r.vs[k].extraRecovery, true); }),
      rr(L.fullRec, function (k) { return k === "ev" ? "–" : recText(r.vs[k].fullRecovery, true); })
    ]);

    setHead("t2-scen", [R.thKm].concat(sel.reduce(function (a, k) { return a.concat([fill(R.thExtra, nm(k)), fill(R.thFull, nm(k))]); }, [])));
    rows("t2-scen", r.scenarios.map(function (s) {
      return { cells: [grp(s.km) + " " + R.kmMonth].concat(sel.reduce(function (a, k) { return a.concat([recText(s[k].extra, false), recText(s[k].full, false)]); }, [])) };
    }));
    setHead("t2-sched", [R.thYear].concat(sel.reduce(function (a, k) { return a.concat([fill(R.thSave, nm(k)), fill(R.thNet, nm(k))]); }, [])));
    rows("t2-sched", r.schedule.map(function (y) {
      var allOk = sel.every(function (k) { return y.net[k] >= 0; });
      return { cls: allOk ? "is-ok" : "", cells: [String(y.year)].concat(sel.reduce(function (a, k) {
        return a.concat([money(y.save[k]), money(y.net[k]) + (y.net[k] >= 0 ? " ✓" : "")]); }, [])) };
    }));
  }

  /* ---------- tabs ---------- */
  var tabs = $$(".evc-tab");
  function selectTab(name, focus) {
    tabs.forEach(function (t) {
      var on = t.getAttribute("data-tab") === name;
      t.setAttribute("aria-selected", on ? "true" : "false"); t.tabIndex = on ? 0 : -1;
      var p = doc.getElementById(t.getAttribute("aria-controls")); if (p) p.hidden = !on;
      if (on && focus) t.focus();
    });
    state.tab = name;
  }
  tabs.forEach(function (t, i) {
    t.addEventListener("click", function () { selectTab(t.getAttribute("data-tab")); });
    t.addEventListener("keydown", function (e) {
      if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
        e.preventDefault();
        var n = tabs[(i + (e.key === "ArrowRight" ? 1 : tabs.length - 1)) % tabs.length];
        selectTab(n.getAttribute("data-tab"), true);
      }
    });
  });

  /* ---------- help popovers ---------- */
  function closeHelp(except) {
    $$(".evc-q[aria-expanded='true']").forEach(function (b) {
      if (b === except) return;
      b.setAttribute("aria-expanded", "false");
      var h = doc.getElementById(b.getAttribute("aria-controls")); if (h) h.hidden = true;
    });
  }
  doc.addEventListener("click", function (e) {
    var q = e.target.closest(".evc-q");
    if (q) {
      var h = doc.getElementById(q.getAttribute("aria-controls")), open = q.getAttribute("aria-expanded") === "true";
      closeHelp(q); q.setAttribute("aria-expanded", open ? "false" : "true"); if (h) h.hidden = open; return;
    }
    if (!e.target.closest(".evc-help")) closeHelp();
  });
  doc.addEventListener("keydown", function (e) { if (e.key === "Escape") closeHelp(); });

  /* ---------- wiring ---------- */
  var timers = {};
  function later(key, fn) { clearTimeout(timers[key]); timers[key] = setTimeout(fn, 160); }
  $$("#panel-loan [data-k]").forEach(function (el) {
    el.addEventListener(el.tagName === "SELECT" ? "change" : "input", function () { later("t1", calc1); });
    el.addEventListener("blur", function () { fmtField(el); });
    el.addEventListener("focus", function () { if (el.hasAttribute("data-money")) el.value = el.value.replace(/,/g, ""); });
  });
  $$("#panel-running [data-k]").forEach(function (el) {
    el.addEventListener("input", function () { later("t2", calc2); updatePill(); });
    el.addEventListener("blur", function () { fmtField(el); });
    el.addEventListener("focus", function () { if (el.hasAttribute("data-money")) el.value = el.value.replace(/,/g, ""); });
  });

  function scrollTo(el) {
    if (!el) return;
    var reduce = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
    el.scrollIntoView({ behavior: reduce ? "auto" : "smooth", block: "start" });
  }
  $$("[data-calc]").forEach(function (b) {
    b.addEventListener("click", function () {
      var t = b.getAttribute("data-calc");
      if (t === "1") { calc1(); scrollTo($("#t1-results")); } else { calc2(); scrollTo($("#t2-results")); }
    });
  });
  $$("[data-reset]").forEach(function (b) {
    b.addEventListener("click", function () {
      var panel = doc.getElementById(b.getAttribute("data-reset"));
      $$("[data-k]", panel).forEach(function (el) {
        if (el.tagName === "SELECT") el.selectedIndex = 0; else { el.value = el.getAttribute("data-default"); fmtField(el); }
        el.classList.remove("is-invalid");
      });
      closeHelp();
      if (panel.id === "panel-loan") calc1(); else calc2();
    });
  });

  var send = $("#t1-send");
  if (send) send.addEventListener("click", function () {
    if (!state.last1) calc1();
    if (!state.last1) return;
    var f = $("#panel-running [data-k='evCost']");
    f.value = Math.round(state.last1.trueCost); fmtField(f);
    calc2(); selectTab("running");
    setText("t2-sent", R.sent); var s = $("#t2-sent"); s.hidden = false; setTimeout(function () { s.hidden = true; }, 6000);
    scrollTo($("#evc-tabs"));
  });

  /* ---------- currency ---------- */
  function applyCurrency() {
    var c = CUR[state.cur];
    $$("[data-cur-sym]").forEach(function (el) { el.textContent = c.sym; });
    $$("[data-money]").forEach(fmtField);
    var note = $("#evc-cur-note");
    if (note) { note.hidden = state.cur === "INR"; note.textContent = R.curNote ? fill(R.curNote, { c: state.cur }) : ""; }
    if (state.last1 || true) { calc1(); calc2(); }
  }
  window.addEventListener("ef:currency-change", function (e) {
    if (!isEn) return;
    var code = e && e.detail && e.detail.currency;
    if (CUR[code]) { state.cur = code; applyCurrency(); }
  });

  /* deep links: #tab-running / #tab-loan */
  var h = (location.hash || "").replace("#", "");
  selectTab(h === "tab-running" ? "running" : "loan");
  applySelection();
  applyCurrency();
})();
