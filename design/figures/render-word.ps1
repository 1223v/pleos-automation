param([Parameter(Mandatory)][string]$InputDocx,[Parameter(Mandatory)][string]$OutputPdf)
# Opens the DOCX read-only in a hidden Word instance, exports PDF and prints the page count.
$ErrorActionPreference = 'Stop'
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
$word.AutomationSecurity = 3
try {
  $doc = $word.Documents.Open($InputDocx, $false, $true, $false)
  try {
    $doc.Repaginate()
    $doc.ExportAsFixedFormat($OutputPdf, 17)
    Write-Output ("Word rendered {0} pages" -f $doc.ComputeStatistics(2))
  } finally {
    $save = [ref]0
    $doc.Close($save)
  }
} finally {
  $save = [ref]0
  $word.Quit($save)
  [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($word)
}
