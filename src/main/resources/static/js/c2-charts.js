/**
 * C2 Charts Engine — Tactical Chart.js configuration for NASA Dark Mode
 * No gridlines, gradient fills, themed colors per ART_DIRECTION §8
 * Includes: Crosshair vertical line plugin (§6.8 compliance)
 */
const c2Charts = {
  charts: {},

  /**
   * Chart.js Plugin: Vertical Crosshair Line
   * Draws a thin vertical line at the cursor position on hover.
   * Mimics NASA telemetry dashboard crosshair behavior.
   */
  crosshairPlugin: {
    id: 'c2Crosshair',
    afterDraw(chart) {
      if (chart.tooltip?._active?.length) {
        const x = chart.tooltip._active[0].element.x;
        const yAxis = chart.scales.y;
        const ctx = chart.ctx;
        ctx.save();
        ctx.beginPath();
        ctx.moveTo(x, yAxis.top);
        ctx.lineTo(x, yAxis.bottom);
        ctx.lineWidth = 1;
        ctx.strokeStyle = 'rgba(0, 240, 255, 0.35)';
        ctx.setLineDash([4, 3]);
        ctx.stroke();
        ctx.restore();
      }
    }
  },

  /** Common dark-mode options for all Chart.js instances */
  darkOpts(hasScales = true) {
    const opts = {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 600, easing: 'easeOutQuart' },
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          position: 'bottom',
          labels: { color: '#7C8BA1', font: { family: "'Space Grotesk'", size: 10 }, boxWidth: 10, padding: 12 }
        },
        tooltip: {
          backgroundColor: '#1A2233',
          titleColor: '#F2F6FA',
          bodyColor: '#7C8BA1',
          borderColor: 'rgba(0,240,255,.22)',
          borderWidth: 1,
          titleFont: { family: "'JetBrains Mono'", size: 11, weight: 600 },
          bodyFont: { family: "'JetBrains Mono'", size: 11 },
          padding: 10,
          displayColors: true,
          caretSize: 0,
          cornerRadius: 0
        }
      }
    };
    if (hasScales) {
      opts.scales = {
        x: {
          ticks: { color: '#4B5566', font: { family: "'JetBrains Mono'", size: 9 } },
          grid: { display: false },
          border: { color: 'rgba(255,255,255,.06)' }
        },
        y: {
          ticks: { color: '#4B5566', font: { family: "'JetBrains Mono'", size: 9 } },
          grid: { color: 'rgba(255,255,255,.03)' },
          border: { display: false }
        }
      };
    }
    return opts;
  },

  /** Create gradient fill for area charts */
  gradient(ctx, colorHex, h = 240) {
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, colorHex + '40');
    g.addColorStop(1, colorHex + '00');
    return g;
  },

  init() {
    // Register crosshair plugin globally for all Chart.js instances
    if (typeof Chart !== 'undefined') {
      Chart.register(this.crosshairPlugin);
    }
  }
};

window.c2Charts = c2Charts;

