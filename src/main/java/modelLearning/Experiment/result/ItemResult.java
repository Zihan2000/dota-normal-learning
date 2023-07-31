package modelLearning.Experiment.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Field;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ItemResult {
    private double costTime;
    private double membershipCount;
    private double equivalenceCount;
    private double hTransitionCount;
    private double hLocationCount;
    private double observationTableSize;
    private double consistentCostTime;
    private int membershipMax = Integer.MIN_VALUE;
    private int membershipMin = Integer.MAX_VALUE;
    private int equivalenceMax = Integer.MIN_VALUE;
    private int equivalenceMin = Integer.MAX_VALUE;

    public static ItemResult plusAndAvg(List<ItemResult> results) {
        int len = results.size();
        Field[] fields = ItemResult.class.getDeclaredFields();
        ItemResult first = results.get(0);
        for (int i = 1; i < results.size(); i++) {
            ItemResult cur = results.get(i);
            for (Field field : fields) {
                String type = field.getGenericType().toString();
                if(!type.equals("double")) continue;
                try {
                    double curVal = field.getDouble(cur);
                    if ("membershipCount".equals(field.getName())) {
                        first.setMembershipMax(Math.max(first.getMembershipMax(), (int)curVal));
                        first.setMembershipMin(Math.min(first.getMembershipMin(), (int)curVal));
                    }
                    if("equivalenceCount".equals(field.getName())) {
                        first.setEquivalenceMax(Math.max(first.getEquivalenceMax(), (int)curVal));
                        first.setEquivalenceMin(Math.min(first.getEquivalenceMin(), (int)curVal));
                    }
                    field.setDouble(first, curVal + field.getDouble(first));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        for (Field field : fields) {
            String type = field.getGenericType().toString();
            if(!type.equals("double")) continue;
            try {
                field.setDouble(first, field.getDouble(first) /len);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return first;
    }
}
