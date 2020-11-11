package net.minestom.server.data;

import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minestom.server.utils.PrimitiveConversion;
import net.minestom.server.utils.binary.BinaryReader;
import net.minestom.server.utils.binary.BinaryWriter;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SerializableData} implementation based on {@link DataImpl}.
 */
public class SerializableDataImpl extends DataImpl implements SerializableData {

    /**
     * Class name -> Class
     * Used to cache class instances so we don't load them by name every time
     */
    private static final ConcurrentHashMap<String, Class> nameToClassMap = new ConcurrentHashMap<>();

    /**
     * Sets a value to a specific key.
     * <p>
     * WARNING: the type needs to be registered in {@link DataManager}.
     *
     * @param key   the key
     * @param value the value object
     * @param type  the value type
     * @param <T>   the value generic
     * @throws UnsupportedOperationException if {@code type} is not registered in {@link DataManager}
     */
    @Override
    public <T> void set(@NotNull String key, @Nullable T value, @Nullable Class<T> type) {
        if (type != null && DATA_MANAGER.getDataType(type) == null) {
            throw new UnsupportedOperationException("Type " + type.getName() + " hasn't been registered in DataManager#registerType");
        }

        super.set(key, value, type);
    }

    @NotNull
    @Override
    public Data copy() {
        SerializableDataImpl data = new SerializableDataImpl();
        data.data.putAll(this.data);
        data.dataType.putAll(this.dataType);
        return data;
    }

    @NotNull
    @Override
    public byte[] getSerializedData(@NotNull Object2ShortMap<String> typeToIndexMap, boolean indexed) {
        // Get the current max index, it supposes that the index keep being incremented by 1
        short lastIndex = (short) typeToIndexMap.size();

        // Main buffer containing the data
        BinaryWriter binaryWriter = new BinaryWriter();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();

            final Class type = dataType.get(key);
            final short typeIndex;
            {
                // Find the type name
                final String encodedType = PrimitiveConversion.getObjectClassString(type.getName()); // Data type (fix for primitives)

                // Find the type index
                if (typeToIndexMap.containsKey(encodedType)) {
                    // Get index
                    typeIndex = typeToIndexMap.getShort(encodedType);
                } else {
                    // Create new index
                    typeToIndexMap.put(encodedType, ++lastIndex);
                    // Set index
                    typeIndex = lastIndex;
                }
            }


            // Write the data type index
            binaryWriter.writeShort(typeIndex);

            // Write the data key
            binaryWriter.writeSizedString(key);

            // Write the data (no length)
            final DataType dataType = DATA_MANAGER.getDataType(type);
            dataType.encode(binaryWriter, value);
        }

        binaryWriter.writeShort((short) 0); // End of data object

        // Header for type indexes
        if (indexed) {
            // The buffer containing all the index info (class name to class index)
            BinaryWriter indexWriter = new BinaryWriter();
            SerializableData.writeDataIndexHeader(indexWriter, typeToIndexMap);
            // Set the header
            binaryWriter.writeAtStart(indexWriter);
        }

        return binaryWriter.toByteArray();
    }

    @Override
    public void readSerializedData(@NotNull BinaryReader reader, @NotNull Object2ShortMap<String> typeToIndexMap) {
        // Map used to convert an index to the class name (opposite of typeToIndexMap)
        final Short2ObjectMap<String> indexToTypeMap = new Short2ObjectOpenHashMap<>(typeToIndexMap.size());
        {
            // Fill the indexToType map
            for (Object2ShortMap.Entry<String> entry : typeToIndexMap.object2ShortEntrySet()) {
                final String type = entry.getKey();
                final short index = entry.getShortValue();
                indexToTypeMap.put(index, type);
            }
        }

        while (true) {
            // Get the class index
            final short typeIndex = reader.readShort();

            if (typeIndex == 0) {
                // End of data
                break;
            }

            final Class type;
            {
                // Retrieve the class type
                final String className = indexToTypeMap.get(typeIndex);
                type = nameToClassMap.computeIfAbsent(className, s -> {
                    // First time that this type is retrieved
                    try {
                        return Class.forName(className);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                        return null;
                    }
                });

                Check.notNull(type, "The class " + className + " does not exist and can therefore not be loaded.");
            }

            // Get the key
            final String name = reader.readSizedString();

            // Get the data
            final Object value;
            {
                final DataType dataType = DATA_MANAGER.getDataType(type);
                Check.notNull(dataType, "The DataType for " + type + " does not exist or is not registered.");

                value = dataType.decode(reader);
            }

            // Set the data
            set(name, value, type);
        }
    }

}
