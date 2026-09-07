const INR = n => '₹' + Math.round(n).toLocaleString('en-IN');

const VT_LABEL = { twoWheeler: 'Two-wheeler', privateCar: 'Private car', commercial: 'Goods carrier', pccv: 'Passenger vehicle', misc: 'Misc vehicle', twoWheelerHire: 'Two-wheeler (hire)' };
const PT_LABEL = { comprehensive: 'Comprehensive', odOnly: 'OD only', thirdParty: 'Third party only' };

function quoteText(s, agentName) {
  const lines = [
    `*Oriental Insurance – Motor Quote*`,
    `${VT_LABEL[s.vehicleType] || s.vehicleType} · ${PT_LABEL[s.policyType]} · Zone ${s.zone} · ${s.fuel}`,
  ];
  if (s.policyType !== 'thirdParty') lines.push(`IDV ${INR(s.idv)} · age ${s.ageYears} yr · NCB ${s.ncbPct}%`);
  lines.push('');
  if (s.policyType !== 'thirdParty') {
    lines.push(`Basic OD: ${INR(s.odBase)}`);
    if (s.odDiscountAmt) lines.push(`OD discount: -${INR(s.odDiscountAmt)}`);
    if (s.ncbDiscount) lines.push(`NCB: -${INR(s.ncbDiscount)}`);
    lines.push(`Net OD: ${INR(s.netOD)}`);
  }
  if (s.policyType !== 'odOnly') lines.push(`Third party: ${INR(s.tpPremium)}`);
  for (const a of s.addons) if (a.amount) lines.push(`${a.label}: ${INR(a.amount)}`);
  lines.push(`Subtotal: ${INR(s.subtotal)}`, `GST 18%: ${INR(s.gst)}`, `*Total premium: ${INR(s.total)}*`, '');
  lines.push('_Indicative quote, subject to inspection and underwriting._');
  if (agentName) lines.push(`– ${agentName}`);
  return lines.join('\n');
}

function missingText(missing) {
  return [
    'To give a quote I still need:',
    ...missing.map(m => `• ${m}`),
    '',
    'Example: *quote bike 125cc 2021 idv 60000*',
    'or: *quote car 1200cc reg 2019 idv 4.5 lakh zone A ncb 25 zero dep*',
  ].join('\n');
}

const HELP = [
  '*What I can do automatically*',
  '1. Send me photos → I merge them into one PDF and send it back.',
  '   (send *pdf* to merge right away instead of waiting)',
  '2. Ask for a quote in one line, e.g.',
  '   *quote bike 125cc 2021 idv 60000*',
  '   *quote car 1200cc 2019 idv 4.5 lakh zone A ncb 25 zero dep diesel*',
  '   *tp only activa 2018*',
  '3. Send *help* to see this again.',
].join('\n');

module.exports = { quoteText, missingText, HELP, INR };
