const reportList = document.querySelector("#myReportList");
const reportCount = document.querySelector("#reportCount");
const loadMoreReports = document.querySelector("#loadMoreReports");
const receivedReportList = document.querySelector("#receivedReportList");
const receivedReportCount = document.querySelector("#receivedReportCount");
const loadMoreReceivedReports = document.querySelector("#loadMoreReceivedReports");
let reportPage = 0;
let collectedReports = [];
let receivedPage = 0;
let receivedReports = [];

function reportEscape(value) { return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;"); }
function reportLabel(value) { return ({ FRAUD:"疑似诈骗或虚假信息", PROHIBITED_CONTENT:"违规内容", HARASSMENT:"骚扰或不友善行为", SPAM:"垃圾广告", OTHER:"其他问题" })[value] || value; }
function reportStatus(value) { return ({ OPEN:["处理中","pending"], RESOLVED:["举报成立","completed"], DISMISSED:["已驳回","cancelled"] })[value] || [value,""]; }
function reportTarget(value) { return ({ ITEM:"商品", MESSAGE:"留言", USER:"用户" })[value] || value; }
function reportTime(value) { return value ? String(value).replace("T", " ").slice(0, 16) : ""; }

function renderReports() {
    reportCount.textContent = `${collectedReports.length} 条记录`;
    reportList.innerHTML = collectedReports.length ? collectedReports.map(report => {
        const status = reportStatus(report.status);
        return `<article class="report-card"><div class="report-card-top"><span class="status-badge ${status[1]}">${status[0]}</span><time>${reportEscape(reportTime(report.createdAt))}</time></div><h3>${reportTarget(report.targetType)}：${reportEscape(report.targetSummary)}</h3><p><strong>${reportLabel(report.reasonCode)}</strong> · ${reportEscape(report.description)}</p>${report.resolutionNote ? `<div class="report-resolution"><strong>处理说明</strong><p>${reportEscape(report.resolutionNote)}</p></div>` : ""}</article>`;
    }).join("") : '<p class="empty-state">你还没有提交过举报。</p>';
}

async function loadReports(append = false) {
    const user = await requireAuthenticatedUser({ message:"登录后才能查看举报记录，是否前往登录？", returnTo:"reports.html" });
    if (!user) return;
    const result = await request(`/reports/mine?page=${reportPage}&size=20`);
    if (!result.success) { reportCount.textContent = "加载失败"; reportList.innerHTML = `<p class="empty-state" role="alert">${reportEscape(result.message || "暂时无法加载")}</p>`; return; }
    collectedReports = append ? collectedReports.concat(result.data.reports || []) : (result.data.reports || []);
    loadMoreReports.hidden = !result.data.hasNext;
    renderReports();
}

loadMoreReports.addEventListener("click", () => { reportPage += 1; loadReports(true); });
function renderReceivedReports() {
    receivedReportCount.textContent = `${receivedReports.length} 条记录`;
    receivedReportList.innerHTML = receivedReports.length ? receivedReports.map(report => {
        const status = reportStatus(report.status);
        return `<article class="report-card"><div class="report-card-top"><span class="status-badge ${status[1]}">${status[0]}</span><time>${reportEscape(reportTime(report.resolvedAt || report.createdAt))}</time></div><h3>${reportTarget(report.targetType)}：${reportEscape(report.targetSummary)}</h3><p><strong>${reportLabel(report.reasonCode)}</strong></p>${report.resolutionNote ? `<div class="report-resolution"><strong>管理员处理说明</strong><p>${reportEscape(report.resolutionNote)}</p></div>` : ""}</article>`;
    }).join("") : '<p class="empty-state">暂无与你相关的治理结果。</p>';
}
async function loadReceivedReports(append = false) {
    const result = await request(`/reports/received?page=${receivedPage}&size=20`);
    if (!result.success) { receivedReportCount.textContent = "加载失败"; receivedReportList.innerHTML = `<p class="empty-state" role="alert">${reportEscape(result.message || "暂时无法加载")}</p>`; return; }
    receivedReports = append ? receivedReports.concat(result.data.reports || []) : (result.data.reports || []);
    loadMoreReceivedReports.hidden = !result.data.hasNext;
    renderReceivedReports();
}
loadMoreReceivedReports.addEventListener("click", () => { receivedPage += 1; loadReceivedReports(true); });
loadReports();
loadReceivedReports();
