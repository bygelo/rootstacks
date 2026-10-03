// Ghidra postScript. Emit the decompile work-queue for a binary: one row per
// function NOT yet reversed (default FUN_* name) first, biggest first. This is
// the "one function at a time" unit the worker swarm consumes — the MW2 shape.
// Java (not Jython): Ghidra 12 dropped bundled Python; Java scripts need no deps.
// Invoked by bs-ghidra: -postScript ExportManifest.java <out.tsv>
//@category rootstacks
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionManager;
import ghidra.program.model.address.Address;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ExportManifest extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        String outPath = (args.length > 0)
            ? args[0]
            : new File(System.getProperty("user.dir"), "manifest.tsv").getPath();

        FunctionManager fm = currentProgram.getFunctionManager();
        List<String[]> rows = new ArrayList<>();
        for (Function fn : fm.getFunctions(true)) {   // true = forward order
            String name = fn.getName();
            Address ep = fn.getEntryPoint();
            long size = fn.getBody().getNumAddresses();
            boolean def = name.startsWith("FUN_") || name.startsWith("thunk_FUN_");
            String status = def ? "todo" : "named";
            String kind = fn.isThunk() ? "thunk" : "func";
            rows.add(new String[]{ status, "0x" + ep.toString(), name, Long.toString(size), kind });
        }
        // todo first; largest first within each group (big functions = most signal)
        rows.sort((a, b) -> {
            int ta = a[0].equals("todo") ? 0 : 1;
            int tb = b[0].equals("todo") ? 0 : 1;
            if (ta != tb) return ta - tb;
            return Long.compare(Long.parseLong(b[3]), Long.parseLong(a[3]));
        });

        try (PrintWriter w = new PrintWriter(new FileWriter(outPath))) {
            w.println("status\taddr\tname\tsize_bytes\tkind");
            for (String[] r : rows) w.println(String.join("\t", r));
        }
        int todo = 0;
        for (String[] r : rows) if (r[0].equals("todo")) todo++;
        println("[bs-ghidra] manifest: " + rows.size() + " functions, " + todo + " todo -> " + outPath);
    }
}
