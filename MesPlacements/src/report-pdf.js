import { jsPDF } from 'jspdf';
import { autoTable } from 'jspdf-autotable';
import { formatMillis } from './report-data.js';
// Standard PDF fonts support French accents; normalize nonbreaking spaces.
const clean = value => String(value).replace(/[\u202f\u00a0]/g, ' ').replace(/’/g, "'");

export function createReportPdf({ report, filterLabels, date, tables }) {
  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
  doc.setProperties({ title: report.title, subject: filterLabels.join(' ; '), author: 'Mes Placements' });
  const margin = 14;
  const width = 182;
  let y = 20;
  function text(value, size = 9, bold = false) {
    doc.setFont('helvetica', bold ? 'bold' : 'normal');
    doc.setFontSize(size);
    const lines = doc.splitTextToSize(clean(value), width);
    for (const line of lines) {
      if (y > 272) { doc.addPage(); y = 20; }
      doc.text(line, margin, y);
      y += size * .45;
    }
    y += 3;
  }
  text('MES PLACEMENTS', 9, true);
  text(report.title, 18, true);
  text(filterLabels.join(' · '));
  text(`Édité le ${date} · Devise : TND · Montants à trois décimales`, 8);
  text('Totaux arithmétiques, pertes comprises. Statuts indiqués sur chaque pièce. Aucun impôt ni compensation fiscale calculé.', 8);
  function table(head, rows) {
    const sourceRows = [];
    const body = rows.map(item => {
      sourceRows.push(item);
      const values = item.values || item;
      if (item.kind === 'group') return [{ content: clean(values[0]), colSpan: head.length }];
      if (item.kind === 'total') return [
        { content: clean(values[0]), colSpan: head.length - 2 },
        clean(values.at(-2)), clean(values.at(-1)),
      ];
      return values.map(clean);
    });
    function badge(column, value) {
      if (column === 'Imposition' && value === 'Exonéré') return { fill: [253, 240, 245], text: [140, 77, 99], border: [243, 212, 223] };
      if (column === 'Imposition' && (value === 'Net d’impôts' || value === "Net d'impôts")) return { fill: [234, 243, 255], text: [60, 111, 170], border: [207, 226, 255] };
      if (column === 'Déclaration' && value === 'Déjà déclaré') return { fill: [223, 243, 229], text: [36, 92, 56], border: [188, 220, 198] };
      return null;
    }
    autoTable(doc, {
      startY: y, margin: { top: 20, right: margin, bottom: 20, left: margin },
      head: [head.map(clean)], body, theme: 'plain', showHead: 'everyPage', rowPageBreak: 'avoid',
      styles: { font: 'helvetica', fontSize: 8, cellPadding: { top: 2.8, right: 2, bottom: 2.8, left: 2 }, textColor: 25, lineColor: [229, 234, 230], lineWidth: { top: 0, right: 0, bottom: .12, left: 0 }, overflow: 'linebreak', valign: 'middle' },
      headStyles: { fillColor: [239, 239, 239], textColor: 20, fontStyle: 'bold', lineColor: [190, 199, 193], lineWidth: { top: 0, right: 0, bottom: .35, left: 0 } },
      didParseCell(data) {
        if (data.section !== 'body') return;
        const row = sourceRows[data.row.index];
        if (row.kind === 'group') {
          data.cell.styles.fontStyle = 'bold';
          data.cell.styles.textColor = [20, 96, 74];
          data.cell.styles.fillColor = row.level === 1 ? [237, 246, 240] : row.level === 2 ? [243, 247, 244] : [248, 250, 248];
          if (data.column.index === 0) data.cell.styles.cellPadding = { top: 2.8, right: 2, bottom: 2.8, left: 2 + (row.level - 1) * 4 };
        } else if (row.kind === 'total') {
          data.cell.styles.fontStyle = 'bold';
          data.cell.styles.fillColor = [250, 252, 251];
          data.cell.styles.lineWidth = { top: .25, right: 0, bottom: 0, left: 0 };
          data.cell.styles.cellPadding = { top: 2.8, right: 2, bottom: 5, left: 2 };
          if (data.column.index === 0) data.cell.styles.cellPadding = { top: 2.8, right: 2, bottom: 2.8, left: 2 + (row.level - 1) * 4 };
        }
        const column = head[data.column.index];
        if (badge(column, data.cell.raw)) data.cell.text = [];
      },
      didDrawCell(data) {
        if (data.section !== 'body') return;
        const value = clean(data.cell.raw);
        const style = badge(head[data.column.index], data.cell.raw);
        if (!style) return;
        let fontSize = 6.5;
        doc.setFont('helvetica', 'bold'); doc.setFontSize(fontSize);
        const available = data.cell.width - 3;
        const natural = doc.getTextWidth(value) + 4;
        if (natural > available) fontSize *= available / natural;
        doc.setFontSize(fontSize);
        const pillWidth = Math.min(available, doc.getTextWidth(value) + 4);
        const pillHeight = 5.4;
        const x = data.cell.x + 1.5;
        const yBadge = data.cell.y + (data.cell.height - pillHeight) / 2;
        doc.setFillColor(...style.fill); doc.setDrawColor(...style.border); doc.setLineWidth(.15);
        doc.roundedRect(x, yBadge, pillWidth, pillHeight, 1.4, 1.4, 'FD');
        doc.setTextColor(...style.text);
        doc.text(value, x + pillWidth / 2, yBadge + 3.55, { align: 'center' });
      },
      columnStyles: { [head.length - 2]: { halign: 'right', cellWidth: 29 }, [head.length - 1]: { halign: 'right', cellWidth: 27 } },
    });
    y = doc.lastAutoTable.finalY + 7;
  }
  for (const section of tables) {
    if (y > 240) { doc.addPage(); y = 20; }
    text(section.title, 11, true);
    table(section.head, section.rows);

  }
  // Keep the complete closing band together, including large signed amounts.
  if (y > 245) { doc.addPage(); y = 20; }
  doc.setFillColor(246); doc.setDrawColor(185); doc.setLineWidth(.25);
  doc.roundedRect(margin, y, width, 26, 1.5, 1.5, 'FD');
  doc.setDrawColor(100); doc.setLineWidth(.5); doc.line(margin, y, margin + width, y);
  doc.setTextColor(25); doc.setFont('helvetica', 'bold'); doc.setFontSize(10);
  doc.setTextColor(70, 132, 107);
  doc.setFontSize(7);
  doc.text('RECAPITULATIF', margin + 5, y + 6);
  doc.setTextColor(25);
  doc.setFontSize(10);
  doc.text('Total général', margin + 5, y + 12);
  doc.setFont('helvetica', 'normal'); doc.setFontSize(7);
  doc.text(clean(`${report.totals.count} pièce${report.totals.count === 1 ? '' : 's'} dans le rapport sélectionné`), margin + 5, y + 19);
  const amounts = [
    { label: 'Montant total', value: report.totals.montant, right: margin + 118 },
    { label: 'Retenue à la source', value: report.totals.rs, right: margin + width - 5 },
  ];
  for (const item of amounts) {
    doc.setFont('helvetica', 'normal'); doc.setFontSize(8);
    doc.text(item.label, item.right, y + 8, { align: 'right' });
    const value = clean(`${formatMillis(item.value)} TND`);
    doc.setFont('helvetica', 'bold'); doc.setFontSize(12);
    if (doc.getTextWidth(value) > 60) doc.setFontSize(12 * 60 / doc.getTextWidth(value));
    doc.text(value, item.right, y + 18, { align: 'right' });
  }
  const pages = doc.getNumberOfPages();
  for (let page = 1; page <= pages; page++) {
    doc.setPage(page);
    doc.setFont('helvetica', 'normal'); doc.setFontSize(8); doc.setTextColor(90);
    if (page > 1) doc.text(clean(`Mes Placements · ${report.title}`), margin, 12);
    doc.text(clean(`Édité le ${date}`), margin, 287);
    doc.text(`${page} / ${pages}`, 196, 287, { align: 'right' });
  }
  return doc;
}

export async function previewReportPdf(snapshot) {
  const doc = createReportPdf(snapshot);
  const filename = `mes-placements-${snapshot.settings.type}-${new Date().toISOString().slice(0, 10)}.pdf`;
  const url = URL.createObjectURL(doc.output('blob'));
  const dialog = document.createElement('dialog');
  dialog.className = 'pdf-preview-dialog';
  dialog.setAttribute('aria-labelledby', 'pdf-preview-title');
  dialog.innerHTML = `
    <header><h2 id="pdf-preview-title">Aperçu du PDF</h2><button type="button" class="text-button pdf-preview-close" aria-label="Fermer l’aperçu">Fermer ×</button></header>
    <iframe title="Aperçu du rapport PDF"></iframe>
    <footer><button type="button" class="text-button pdf-preview-cancel">Fermer</button><button type="button" class="icon-button pdf-preview-print">Imprimer</button><button type="button" class="primary-button pdf-preview-save">Enregistrer le PDF</button></footer>`;
  const frame = dialog.querySelector('iframe');
  frame.src = url;
  document.body.append(dialog);
  const close = () => dialog.close();
  dialog.querySelector('.pdf-preview-close').addEventListener('click', close);
  dialog.querySelector('.pdf-preview-cancel').addEventListener('click', close);
  dialog.querySelector('.pdf-preview-print').addEventListener('click', () => frame.contentWindow?.print());
  dialog.querySelector('.pdf-preview-save').addEventListener('click', async event => {
    const button = event.currentTarget;
    button.disabled = true;
    button.textContent = 'Enregistrement…';
    try { await doc.save(filename, { returnPromise: true }); }
    finally { button.disabled = false; button.textContent = 'Enregistrer le PDF'; }
  });
  dialog.addEventListener('close', () => {
    URL.revokeObjectURL(url);
    dialog.remove();
  }, { once: true });
  dialog.showModal();
}

export const saveReportPdf = previewReportPdf;
