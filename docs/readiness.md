# Readiness (recovery) — model

A local, transparent score from 0 to 100 that estimates how recovered you are today. **It is not a
medical metric.** Wearable scores (Oura, Garmin Body Battery, Whoop) are proprietary; here we use
public, documented components and combine them with open weights, meant to be read and adjusted.

## Factors (each 0..100) and weights

| Factor | Weight | How |
|---|---|---|
| HRV vs baseline | 0.30 | Last night's ln(rMSSD) against the 7-day average, scaled by its deviation. Higher = more recovered. |
| Resting HR vs baseline | 0.20 | Today's resting HR against the 7-day mean/dev. Lower = more recovered. |
| Sleep | 0.30 | Last night's hours against a target (8 h by default), with optional quality. |
| Training load (ACWR) | 0.20 | Acute (7 days) / chronic (~28-day weekly average). Zone 0.8–1.3 = optimal; spikes > 1.5 penalize. |

The total is the weighted average of the **available** factors (the weights are renormalized over the
present ones), so it degrades gracefully. `confidence` reflects how many factors there were.

When the watch data is missing, the **manual log** is used (sleep in hours/quality and workout in
minutes + RPE 1–10), persisted locally per day.

## Sources (components, not a validated combined formula)
- HRV: a 7-day moving baseline and the "smallest worthwhile change" (Plews approach; ln rMSSD).
  Elite HRV: readiness from changes in ln(rMSSD); 7-day coefficient of variation.
- ACWR: Gabbett — acute:chronic ratio; zone ~0.8–1.3, rising risk above it; coupled versions
  (1 wk / 4 wk) vs uncoupled (1 wk / 3 wk) almost identical (Gabbett et al., 2019).
- Resting HR and sleep duration as commonly used recovery markers.

Links: Elite HRV (help.elitehrv.com), Plews/Altini (marcoaltini.substack.com),
Gabbett (bjsm.bmj.com/content/50/8/471). The combined model and the weights are our own and should
be calibrated with your own data.
