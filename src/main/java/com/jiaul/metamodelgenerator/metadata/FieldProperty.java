package com.jiaul.metamodelgenerator.metadata;

/**
 * Metadata descriptor for an entity field or relation path, holding its path/name,
 * primary type, and element type (if collection/array).
 * <p>
 * Implements {@link CharSequence} so it can be passed directly to string concatenation,
 * JPQL queries, and Criteria API calls without manual {@code .name()} extraction.
 *
 * @param <T>         the primary field type (e.g. {@code String.class}, {@code List.class})
 * @param <E>         the element type if the field is a collection (or {@code Void.class} if scalar)
 * @param name        the field path (e.g. {@code "id"}, {@code "courses.title"})
 * @param type        the runtime class representing the field type
 * @param elementType the runtime class of the collection element, or {@code null}
 */
public record FieldProperty<T, E>(
        String name,
        Class<T> type,
        Class<E> elementType
) implements CharSequence {

    @Override
    public int length() {
        return name != null ? name.length() : 0;
    }

    @Override
    public char charAt(int index) {
        if (name == null) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (name == null) {
            throw new StringIndexOutOfBoundsException(start);
        }
        return name.subSequence(start, end);
    }

    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    public Class<E> getElementType() {
        return elementType;
    }

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
