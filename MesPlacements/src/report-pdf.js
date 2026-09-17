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
    const body = rows.map(row => row.map(clean));
    autoTable(doc, {
      startY: y, margin: { top: 20, right: margin, bottom: 20, left: margin },
      head: [head.map(clean)], body, theme: 'grid', showHead: 'everyPage', rowPageBreak: 'avoid',
      styles: { font: 'helvetica', fontSize: 8, cellPadding: 2, textColor: 25, lineColor: 220, lineWidth: .15, overflow: 'linebreak' },
      headStyles: { fillColor: [239, 239, 239], textColor: 20, fontStyle: 'bold' },
      didParseCell(data) {
        if (data.section !== 'body') return;
        const column = head[data.column.index];
        if (column === 'Imposition' && data.cell.raw === 'Exonéré') {
          data.cell.styles.fillColor = [242, 242, 242];
          data.cell.styles.textColor = 20;
        }
        if (column === 'Déclaration' && data.cell.raw === 'Déjà déclaré') data.cell.styles.fontStyle = 'bold';
      },
      didDrawCell(data) {
        if (data.section === 'body' && head[data.column.index] === 'Déclaration' && data.cell.raw === 'Déjà déclaré') {
          doc.setDrawColor(150); doc.setLineWidth(.2);
          doc.roundedRect(data.cell.x + .8, data.cell.y + .8, data.cell.width - 1.6, data.cell.height - 1.6, .7, .7, 'S');
        }
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

export async function saveReportPdf(snapshot) {
  const doc = createReportPdf(snapshot);
  await doc.save(`mes-placements-${snapshot.settings.type}-${new Date().toISOString().slice(0, 10)}.pdf`, { returnPromise: true });
}
