; Pomas maintenance-mode page for the assisted NSIS installer.
; electron-builder calls customInit after initMultiUser, so both the existing
; install mode and its exact directory are already known here.

!include "nsDialogs.nsh"

Var PomasExistingInstall

!macro customInit
  StrCpy $PomasExistingInstall "0"

  ${If} $hasPerUserInstallation == "1"
    StrCpy $PomasExistingInstall "1"
    StrCpy $INSTDIR "$perUserInstallationFolder"
  ${ElseIf} $hasPerMachineInstallation == "1"
    StrCpy $PomasExistingInstall "1"
    StrCpy $INSTDIR "$perMachineInstallationFolder"
  ${EndIf}
!macroend

!macro customWelcomePage
  Page custom PomasMaintenancePage
!macroend

Function PomasMaintenancePage
  ; A clean installation keeps the standard first-install wizard.
  ${If} $PomasExistingInstall != "1"
    Abort
  ${EndIf}

  !insertmacro MUI_HEADER_TEXT "Modify Pomas" "An existing Pomas installation was detected."

  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 58u "Pomas is already installed on this computer.$\r$\n$\r$\nChoose Modify to update the existing application files. Your farm database, settings, and backups are stored separately and will be preserved."
  Pop $1

  ${NSD_CreateRadioButton} 0 68u 100% 14u "Modify the existing Pomas installation"
  Pop $2
  ${NSD_Check} $2

  nsDialogs::Show
FunctionEnd
