; Pomas maintenance-mode page for the assisted NSIS installer.
; electron-builder calls customInit after initMultiUser, so both the existing
; install mode and its exact directory are already known here.

!include "nsDialogs.nsh"

!ifndef BUILD_UNINSTALLER
Var PomasExistingInstall
!endif

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

; The installer include is also compiled while electron-builder builds the
; uninstaller. The uninstaller does not register installer pages, so keep this
; page function out of the uninstaller compilation.
!ifndef BUILD_UNINSTALLER
Function PomasMaintenancePage
  ; A clean installation keeps the standard first-install wizard.
  ${If} $PomasExistingInstall != "1"
    Abort
  ${EndIf}

  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 18u "Modify Pomas"
  Pop $1

  ${NSD_CreateLabel} 0 24u 100% 58u "Pomas is already installed on this computer.$\r$\n$\r$\nChoose Modify to update the existing application files. Your farm database, settings, and backups are stored separately and will be preserved."
  Pop $2

  ${NSD_CreateRadioButton} 0 90u 100% 14u "Modify the existing Pomas installation"
  Pop $3
  ${NSD_Check} $3

  nsDialogs::Show
FunctionEnd
!endif
