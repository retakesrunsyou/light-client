package net.peregrine.client.v26_1;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ResultField;

/** The game's latest profiler results for the pie chart HUD item (handed over each frame while it shows). */
public final class PieData {

    public static volatile ProfileResults results;

    private PieData() {
    }

    static List<Object[]> times(String path) {
        ProfileResults r = results;
        if (r == null || !net.peregrine.client.core.modules.PieChart.collecting()) {
            return null;
        }
        List<ResultField> fields = r.getTimes(path);
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        List<Object[]> out = new ArrayList<Object[]>(fields.size());
        for (ResultField f : fields) {
            out.add(new Object[] {f.name, f.percentage, f.globalPercentage, f.getColor()});
        }
        return out;
    }
}
