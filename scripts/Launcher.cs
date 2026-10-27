using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace DEXLauncher {
    static class Program {
        [STAThread]
        static void Main() {
            try {
                string baseDir = AppDomain.CurrentDomain.BaseDirectory;
                
                // 1. Look for dist/desktop/DEX/DEX.exe
                string distExe = Path.GetFullPath(Path.Combine(baseDir, @"..\dist\desktop\DEX\DEX.exe"));
                if (File.Exists(distExe)) {
                    ProcessStartInfo psi = new ProcessStartInfo(distExe);
                    psi.WorkingDirectory = Path.GetDirectoryName(distExe);
                    Process.Start(psi);
                    return;
                }

                // 2. Look for target jar with javaw
                string jarPath = Path.GetFullPath(Path.Combine(baseDir, @"..\target\dex-ai-assistant-1.0.0-jar-with-dependencies.jar"));
                if (File.Exists(jarPath)) {
                    ProcessStartInfo psi = new ProcessStartInfo("javaw.exe", "-jar \"" + jarPath + "\" --desktop");
                    psi.WorkingDirectory = Path.GetFullPath(Path.Combine(baseDir, @".."));
                    Process.Start(psi);
                    return;
                }

                // 3. Look for installed Program Files DEX
                string progFilesExe = @"C:\Program Files\DEX\DEX.exe";
                if (File.Exists(progFilesExe)) {
                    ProcessStartInfo psi = new ProcessStartInfo(progFilesExe);
                    psi.WorkingDirectory = Path.GetDirectoryName(progFilesExe);
                    Process.Start(psi);
                    return;
                }

                MessageBox.Show("DEX build artifacts not found.\nPlease run DEX.bat or install DEX-Setup.exe.", "DEX AI Assistant", MessageBoxButtons.OK, MessageBoxIcon.Information);
            } catch (Exception ex) {
                MessageBox.Show("Error launching DEX: " + ex.Message, "DEX Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
