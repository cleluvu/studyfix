$ErrorActionPreference = 'Stop'
$speechDoc = (Resolve-Path -LiteralPath '..\output\StudyFix_Loi_Thuyet_Trinh_28_Slide.docx').Path
$speechPdf = Join-Path (Resolve-Path -LiteralPath 'speech-qa').Path 'speech.pdf'
$wordApp = $null
$speechDocument = $null
try {
    $wordApp = New-Object -ComObject Word.Application
    $wordApp.Visible = $false
    $wordApp.DisplayAlerts = 0
    $wordApp.AutomationSecurity = 3
    $speechDocument = $wordApp.Documents.Open($speechDoc, $false, $true, $false)
    $speechDocument.Fields.Update() | Out-Null
    $speechDocument.Repaginate()
    $speechDocument.ExportAsFixedFormat($speechPdf, 17)
    Write-Output ('Rendered pages: ' + $speechDocument.ComputeStatistics(2))
    Write-Output $speechPdf
} finally {
    if ($null -ne $speechDocument) { $speechDocument.Close(0) }
    if ($null -ne $wordApp) { $wordApp.Quit() }
    if ($null -ne $speechDocument) { [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($speechDocument) }
    if ($null -ne $wordApp) { [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($wordApp) }
}
