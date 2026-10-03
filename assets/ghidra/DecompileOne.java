// Ghidra postScript. Decompile exactly one function to C — the worker agent's
// atomic task: take one addr/name from the manifest, get clean C, name it, type
// its args, commit; the overseer (/gate) reviews the commit. Writes C to an out
// file if given (keeps it free of Ghidra log noise), else prints it.
// Invoked by bs-ghidra: -postScript DecompileOne.java <addr-or-name> [outfile]
//@category rootstacks
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;
import ghidra.program.model.address.Address;
import ghidra.util.task.ConsoleTaskMonitor;
import java.io.FileWriter;
import java.io.PrintWriter;

public class DecompileOne extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        if (args.length == 0) {
            println("[bs-ghidra] DecompileOne needs an addr (0x...) or function name");
            return;
        }
        String target = args[0];
        String outFile = (args.length > 1) ? args[1] : null;

        FunctionManager fm = currentProgram.getFunctionManager();
        Function fn = null;
        if (target.startsWith("0x") || target.startsWith("0X")) {
            Address addr = currentProgram.getAddressFactory().getAddress(target.substring(2));
            fn = fm.getFunctionAt(addr);
            if (fn == null) fn = fm.getFunctionContaining(addr);
        } else {
            for (Function c : fm.getFunctions(true)) {
                if (c.getName().equals(target)) { fn = c; break; }
            }
        }
        if (fn == null) {
            println("[bs-ghidra] function not found: " + target);
            return;
        }

        DecompInterface ifc = new DecompInterface();
        ifc.openProgram(currentProgram);
        DecompileResults res = ifc.decompileFunction(fn, 120, new ConsoleTaskMonitor());

        StringBuilder sb = new StringBuilder();
        sb.append("// ==== ").append(fn.getName()).append(" @ ").append(fn.getEntryPoint()).append(" ====\n");
        if (res != null && res.decompileCompleted()) {
            sb.append(res.getDecompiledFunction().getC());
        } else {
            sb.append("// decompile failed: ").append(res != null ? res.getErrorMessage() : "no result");
        }

        if (outFile != null) {
            try (PrintWriter w = new PrintWriter(new FileWriter(outFile))) { w.print(sb.toString()); }
            println("[bs-ghidra] decompiled " + fn.getName() + " -> " + outFile);
        } else {
            println(sb.toString());
        }
    }
}
