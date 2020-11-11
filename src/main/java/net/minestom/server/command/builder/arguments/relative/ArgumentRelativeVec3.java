package net.minestom.server.command.builder.arguments.relative;

import net.minestom.server.utils.Vector;
import net.minestom.server.utils.location.RelativeVec;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a {@link Vector} with 3 floating numbers (x;y;z) which can take relative coordinates.
 * <p>
 * Example: -1.2 ~ 5
 */
public class ArgumentRelativeVec3 extends ArgumentRelativeVec {

    public ArgumentRelativeVec3(@NotNull String id) {
        super(id, 3);
    }

    @NotNull
    @Override
    public RelativeVec parse(@NotNull String value) {
        final String[] split = value.split(" ");

        Vector vector = new Vector();
        boolean relativeX = false;
        boolean relativeY = false;
        boolean relativeZ = false;

        for (int i = 0; i < split.length; i++) {
            final String element = split[i];
            if (element.startsWith(RELATIVE_CHAR)) {

                if (i == 0) {
                    relativeX = true;
                } else if (i == 1) {
                    relativeY = true;
                } else if (i == 2) {
                    relativeZ = true;
                }

                if (element.length() != RELATIVE_CHAR.length()) {
                    final String potentialNumber = element.substring(1);
                    final float number = Float.parseFloat(potentialNumber);
                    if (i == 0) {
                        vector.setX(number);
                    } else if (i == 1) {
                        vector.setY(number);
                    } else if (i == 2) {
                        vector.setZ(number);
                    }
                }

            } else {
                final float number = Float.parseFloat(element);
                if (i == 0) {
                    vector.setX(number);
                } else if (i == 1) {
                    vector.setY(number);
                } else if (i == 2) {
                    vector.setZ(number);
                }
            }
        }

        return new RelativeVec(vector, relativeX, relativeY, relativeZ);
    }
}
