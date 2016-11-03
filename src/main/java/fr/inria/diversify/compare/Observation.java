package fr.inria.diversify.compare;

import java.util.*;

/**
 * User: Simon
 * Date: 23/10/15
 * Time: 15:36
 */
public  class Observation {
    public static String junitAssertClassName = "junit.framework.Assert";
    protected Set<String> notDeterministValues;
    protected Map<String, Object> observations;
    public Observation() {
        this.observations = new HashMap<String, Object>();
        this.notDeterministValues = new HashSet<String>();
    }

    public boolean add(String stringObject, Object value) {
        if (!notDeterministValues.contains(stringObject)) {
            if (observations.containsKey(stringObject)) {
                Object oldValue = observations.get(stringObject);
                if(oldValue == null) {
                    if (value == null) {
                        return true;
                    } else {
                        notDeterministValues.add(stringObject);
                        return false;
                    }
                } else if(!equals(oldValue, value)) {
                    notDeterministValues.add(stringObject);
                    return false;
                }
            } else {
                observations.put(stringObject, value);
            }
            return true;
        } else {
            return false;
        }
    }

    public List<String> buildAssert() {
        List<String> asserts = new ArrayList<String>(observations.size());
        for (Map.Entry<String, Object> entry : observations.entrySet()) {
            if(!notDeterministValues.contains(entry.getKey())) {
                Object value = entry.getValue();

                if(value == null) {
                    asserts.add(junitAssertClassName + ".assertNull(" + entry.getKey() + ")");
                } else if (isBoolean(value)) {
                    if ((Boolean) value) {
                        asserts.add(junitAssertClassName + ".assertTrue(" + entry.getKey() + ")");
                    } else {
                        asserts.add(junitAssertClassName + ".assertFalse(" + entry.getKey() + ")");
                    }
                } else if (value.getClass().isArray()) {
                    asserts.add(buildAssertForArray(entry.getKey(), value));
                } else {
                    asserts.add(junitAssertClassName + ".assertEquals(" + entry.getKey() + ", " + printString(value) + ")");
                }
            }
        }
        return asserts;
    }

    protected String buildAssertForArray(String expression, Object array) {
        Random r = new Random();
        String type = array.getClass().getCanonicalName();
        String arrayLocalVar1 = "array_" + Math.abs(r.nextInt());
        String arrayLocalVar2 = "array_" + Math.abs(r.nextInt());


        String forLoop = "\tfor(int ii = 0; ii <" + arrayLocalVar1 + ".length; ii++) {\n\t\t"
                + junitAssertClassName +".assertEquals(" +arrayLocalVar1 +"[ii], " + arrayLocalVar2 + "[ii]);\n\t}";

        return type + " "+ arrayLocalVar1 + " = " + primitiveArrayToString(array) + ";\n\t"
                + type + " " + arrayLocalVar2 + " = " + "(" + type + ")" + expression + ";\n"
                + forLoop;
    }

    protected String printString(Object value) {
        if(value instanceof Double) {
            return value.toString() + "D";
        }
        if(value instanceof Float) {
            return value.toString() + "F";
        }
        if(value instanceof Long) {
            return value.toString() + "L";
        }
        if(value instanceof String) {
            return "\"" + value.toString() + "\"";
        }
        if(value instanceof Character) {
            return "\'" + value.toString() + "\'";
        }
        return value.toString();
    }

    protected boolean isBoolean(Object value) {
        return value instanceof Boolean;
    }

    protected String primitiveArrayToString(Object array) {
        String type = array.getClass().getCanonicalName();

        String tmp;
        if(type.equals("int[]")) {
            tmp = Arrays.toString((int[]) array);
            return "new int[]{" + tmp.substring(1, tmp.length() - 1) + "}";
        }
        if(type.equals("short[]")) {
            tmp = Arrays.toString((short[]) array);
            return "new short[]{" + tmp.substring(1, tmp.length() - 1) + "}";
        }
        if(type.equals("byte[]")) {
            tmp = Arrays.toString((byte[]) array);
            return "new byte[]{" + tmp.substring(1, tmp.length() - 1) + "}";
        }
        if(type.equals("long[]")) {
            tmp = Arrays.toString((long[]) array);
            return "new long[]{" + tmp.substring(1, tmp.length() - 1) + "}";
        }
        if(type.equals("float[]")) {
            tmp = Arrays.toString((float[]) array);
            return "new float[]{" + tmp.substring(1, tmp.length() - 1) + "}";
        }
        if(type.equals("double[]")) {
            tmp = Arrays.toString((double[]) array);
            return "new double[]{" + tmp.substring(1, tmp.length() - 1) + "}";
        }
        if(type.equals("boolean[]")) {
            tmp = Arrays.toString((boolean[]) array);
            return "new boolean[]{" + tmp.substring(1, tmp.length() - 1) + "}";
        }
        if(type.equals("char[]")) {
            char[] arrayChar = (char[]) array;

            if (arrayChar.length == 0) {
                return "new char[]{}";
            }
            if (arrayChar.length == 1) {
                return "new char[]{\'" + arrayChar[0] + "\'}";
            } else {
                String ret = "new char[]{\'" + arrayChar[0];
                for (int i = 1; i < arrayChar.length - 1; i++) {
                    ret += "\',\'" + arrayChar[i];
                }
                return ret + "\'}";
            }
        }
        return null;
    }

    protected boolean equals(Object o1, Object o2) {
        boolean isArray1 = o1.getClass().isArray();
        boolean isArray2 = o2.getClass().isArray();
        if (isArray1 == isArray2) {
            if (isArray1) {
                try {
                    String type = o1.getClass().getCanonicalName();

                    if(type.equals("int[]")) {
                        return Arrays.equals((int[]) o1, (int[])o2);
                    }
                    if(type.equals("short[]")) {
                        return Arrays.equals((short[]) o1, (short[])o2);
                    }
                    if(type.equals("byte[]")) {
                        return Arrays.equals((byte[]) o1, (byte[])o2);
                    }
                    if(type.equals("long[]")) {
                        return Arrays.equals((long[]) o1, (long[])o2);
                    }
                    if(type.equals("float[]")) {
                        return Arrays.equals((float[]) o1, (float[])o2);
                    }
                    if(type.equals("double[]")) {
                        return Arrays.equals((double[]) o1, (double[])o2);
                    }
                    if(type.equals("boolean[]")) {
                        return Arrays.equals((boolean[]) o1, (boolean[])o2);
                    }
                    if(type.equals("char[]")) {
                        return Arrays.equals((char[]) o1, (char[])o2);
                    }
                    return Arrays.equals((Object[]) o1, (Object[]) o2);
                } catch (Exception e) {
                    return false;
                }
            } else {
                return o1.equals(o2);
            }
        } else {
            return false;
            }
        }
}
