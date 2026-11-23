/* EMIFORMULA — EV vs Petrol/Diesel calculation engine (pure functions, no DOM) */
(function () {
  "use strict";

  var MAX_MONTHS = 600; // recovery beyond 50 years is reported as "very long"

  function num(v, d) {
    var n = typeof v === "string" ? Number(v.replace(/,/g, "")) : Number(v);
    return isFinite(n) ? n : (d === undefined ? 0 : d);
  }

  /* Reducing-balance EMI: P*r*(1+r)^n / ((1+r)^n - 1) */
  function emi(principal, annualRate, months) {
    principal = num(principal); months = Math.round(num(months));
    if (principal <= 0 || months <= 0) return 0;
    var r = num(annualRate) / 1200;
    if (r === 0) return principal / months;
    var f = Math.pow(1 + r, months);
    return principal * r * f / (f - 1);
  }

  /* Year-by-year amortization built from the monthly schedule */
  function amortize(principal, annualRate, months) {
    var rows = [], pay = emi(principal, annualRate, months), r = num(annualRate) / 1200;
    var bal = principal, y = 1, yi = 0, yp = 0, open = principal;
    for (var m = 1; m <= months && bal > 0.000001; m++) {
      var interest = bal * r;
      var prin = Math.min(pay - interest, bal);
      bal -= prin; yi += interest; yp += prin;
      if (m % 12 === 0 || m === months) {
        rows.push({ year: y, opening: open, principal: yp, interest: yi, payment: yp + yi, closing: Math.max(0, bal) });
        y++; yi = 0; yp = 0; open = bal;
      }
    }
    return rows;
  }

  /* ---------- Tab 1: old-car exchange + EV loan ---------- */
  function exchangeLoan(i) {
    var errors = [];
    var resale = Math.max(0, num(i.oldResale)), pending = Math.max(0, num(i.pendingLoan));
    var onRoad = num(i.onRoadPrice), cash = Math.max(0, num(i.cashDown));
    var rate = num(i.annualRate, NaN), years = num(i.tenureYears, NaN);
    if (!(onRoad > 0)) errors.push("onRoadPrice");
    if (!(rate >= 0 && rate <= 40)) errors.push("annualRate");
    if (!(years >= 0.5 && years <= 10)) errors.push("tenureYears");
    if (errors.length) return { ok: false, errors: errors };

    var months = Math.round(years * 12);
    var equity = resale - pending;                 // can be negative (pending loan > resale)
    var effectiveDown = equity + cash;
    var netLoan = Math.max(0, onRoad - effectiveDown);
    var monthlyEmi = emi(netLoan, rate, months);
    var totalPayable = monthlyEmi * months;
    var totalInterest = Math.max(0, totalPayable - netLoan);
    var trueCost = onRoad + totalInterest;
    return {
      ok: true, months: months, equity: equity, shortfall: equity < 0 ? -equity : 0,
      effectiveDown: effectiveDown, excessDown: Math.max(0, effectiveDown - onRoad),
      netLoan: netLoan, emi: monthlyEmi, totalPayable: totalPayable, totalInterest: totalInterest,
      trueCost: trueCost, loanShare: netLoan / onRoad,
      cashOutOfPocket: cash + totalPayable,
      inputs: { resale: resale, pending: pending, onRoad: onRoad, cash: cash, rate: rate, years: years },
      schedule: amortize(netLoan, rate, months)
    };
  }

  /* ---------- Tab 2: per-km cost, price recovery, 5-year view ---------- */
  function recovery(amount, monthlySaving, km) {
    if (amount <= 0) return { status: "immediate", months: 0, km: 0 };
    if (!(monthlySaving > 0)) return { status: "never" };
    var m = amount / monthlySaving;
    if (m > MAX_MONTHS) return { status: "veryLong" };
    return { status: "ok", months: m, km: m * km };
  }

  function chargeCost(i) {
    var loss = Math.min(0.5, Math.max(0, num(i.chargingLossPct) / 100));
    var share = Math.min(1, Math.max(0, num(i.publicSharePct) / 100));
    var blended = (1 - share) * num(i.electricityPrice) + share * num(i.publicPrice);
    var gridUnits = num(i.batteryKwh) / (1 - loss);
    return { loss: loss, share: share, blendedPrice: blended, gridUnits: gridUnits, cost: gridUnits * blended };
  }

  var ICE = ["diesel", "petrol", "hybrid"];
  function pick(i, ice) {
    var s = ice === "diesel" ? "diesel" : (ice === "petrol" ? "petrol" : "hybrid");
    return { price: num(i[s + "Price"]), mileage: num(i[s + "Mileage"]), fuel: num(i[s + "FuelPrice"]), service: num(i[s + "Service"]),
      resalePct: num(i[s + "ResalePct"]) };
  }
  function selected(i) {
    var c = Array.isArray(i.compare) ? i.compare : ["diesel", "petrol"];
    return ICE.filter(function (k) { return c.indexOf(k) > -1; });
  }

  function perKm(i, sel) {
    var km = num(i.monthlyKm), annualKm = km * 12, ch = chargeCost(i);
    var out = { charge: ch };
    out.ev = { energy: ch.cost / num(i.realRangeKm), service: num(i.evService) / annualKm, tyre: num(i.evTyreCost) / num(i.evTyreLife) };
    sel.forEach(function (k) {
      var v = pick(i, k);
      out[k] = { energy: v.fuel / v.mileage, service: v.service / annualKm, tyre: num(i.iceTyreCost) / num(i.iceTyreLife) };
    });
    ["ev"].concat(sel).forEach(function (k) { out[k].total = out[k].energy + out[k].service + out[k].tyre; });
    return out;
  }

  function evVsIce(i) {
    var errors = [], sel = selected(i);
    if (!sel.length) return { ok: false, errors: ["compare"] };
    [["monthlyKm", 1], ["evCost", 1], ["batteryKwh", 1], ["realRangeKm", 1], ["evTyreLife", 1], ["iceTyreLife", 1]].forEach(function (r) {
      if (!(num(i[r[0]], NaN) >= r[1])) errors.push(r[0]);
    });
    sel.forEach(function (k) { if (!(num(i[k + "Mileage"], NaN) >= 0.1)) errors.push(k + "Mileage"); });
    var nonneg = ["electricityPrice", "evService", "evTyreCost", "iceTyreCost", "chargerCost", "chargingLossPct", "publicSharePct", "publicPrice", "evResalePct"];
    sel.forEach(function (k) { nonneg.push(k + "Price", k + "FuelPrice", k + "Service", k + "ResalePct"); });
    nonneg.forEach(function (k) { if (!(num(i[k], NaN) >= 0)) errors.push(k); });
    if (num(i.chargingLossPct) >= 50) errors.push("chargingLossPct");
    if (errors.length) return { ok: false, errors: errors };

    var km = num(i.monthlyKm), pk = perKm(i, sel);
    var cars = { ev: { purchase: num(i.evCost) + num(i.chargerCost), resalePct: num(i.evResalePct), resaleBase: num(i.evCost) } };
    sel.forEach(function (k) { var v = pick(i, k); cars[k] = { purchase: v.price, resalePct: v.resalePct, resaleBase: v.price }; });
    ["ev"].concat(sel).forEach(function (k) {
      var c = cars[k];
      c.perKm = pk[k]; c.monthly = pk[k].total * km; c.yearly = c.monthly * 12; c.fiveYearRunning = c.monthly * 60;
      c.fiveYearTotal = c.purchase + c.fiveYearRunning;
      c.resaleValue = c.resaleBase * Math.min(100, Math.max(0, c.resalePct)) / 100;
      c.fiveYearNet = c.fiveYearTotal - c.resaleValue;
    });

    var vs = {}, breakEven = {};
    sel.forEach(function (k) {
      var extra = cars.ev.purchase - cars[k].purchase, ms = cars[k].monthly - cars.ev.monthly;
      vs[k] = { extraPrice: extra, monthlySaving: ms, yearlySaving: ms * 12, fiveYearSaving: ms * 60,
        extraRecovery: recovery(extra, ms, km), fullRecovery: recovery(cars.ev.purchase, ms, km) };
      /* km/month at which EV's 5-year net cost (after resale) equals this car's */
      var c = cars[k], e = cars.ev;
      var fixed = (e.purchase - e.resaleValue) - (c.purchase - c.resaleValue) + 60 * (num(i.evService) - pick(i, k).service) / 12;
      var gap = 60 * ((c.perKm.energy + c.perKm.tyre) - (pk.ev.energy + pk.ev.tyre));
      breakEven[k] = fixed <= 0 ? { status: "always" } : (!(gap > 0) ? { status: "never" } : { status: "ok", km: fixed / gap });
    });

    var scenarios = [500, 1000, 1200, 2000].map(function (k) {
      var row = { km: k };
      sel.forEach(function (ice) {
        var saving = k * ((cars[ice].perKm.energy + cars[ice].perKm.tyre) - (pk.ev.energy + pk.ev.tyre)) + (pick(i, ice).service - num(i.evService)) / 12;
        row[ice] = { monthlySaving: saving, extra: recovery(cars.ev.purchase - cars[ice].purchase, saving, k), full: recovery(cars.ev.purchase, saving, k) };
      });
      return row;
    });

    var schedule = [];
    for (var y = 1; y <= 10; y++) {
      var row = { year: y, save: {}, net: {} };
      sel.forEach(function (k) { var s = vs[k].monthlySaving * 12 * y; row.save[k] = s; row.net[k] = s - vs[k].extraPrice; });
      schedule.push(row);
    }

    var order = ["ev"].concat(sel).sort(function (a, b) { return cars[a].fiveYearNet - cars[b].fiveYearNet; });
    return {
      ok: true, monthlyKm: km, selected: sel, cars: cars, perKm: pk, charge: pk.charge, vs: vs,
      vsDiesel: vs.diesel, vsPetrol: vs.petrol, vsHybrid: vs.hybrid, breakEven: breakEven,
      scenarios: scenarios, schedule: schedule, cheapest5yr: order[0], ranking5yr: order,
      cheapestMargin: cars[order[1]].fiveYearNet - cars[order[0]].fiveYearNet
    };
  }

  /* Duration split used by the hero sentence (rounded to whole months) */
  function splitMonths(months) {
    var m = Math.max(0, Math.round(num(months)));
    return { years: Math.floor(m / 12), months: m % 12, total: m };
  }

  window.EMIFORMULA_EV = {
    emi: emi, amortize: amortize, exchangeLoan: exchangeLoan, evVsIce: evVsIce,
    chargeCost: chargeCost, splitMonths: splitMonths, num: num
  };
})();
