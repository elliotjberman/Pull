#include <jni.h>
#include <jvmti.h>

#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>


#define SETUP_SIGNATURE "Lde/mossgrabers/controller/ableton/push/PushControllerSetup;"
#define STATUS_PATH "/tmp/pull-push2-hotswap"


static void write_status (const char *format, ...)
{
    FILE *file = fopen (STATUS_PATH, "w");
    if (file == NULL)
        return;

    time_t now = time (NULL);
    fprintf (file, "%lld ", (long long) now);

    va_list arguments;
    va_start (arguments, format);
    vfprintf (file, format, arguments);
    va_end (arguments);

    fputc ('\n', file);
    fclose (file);
}


static jint fail (jvmtiEnv *jvmti, jvmtiError error, const char *operation)
{
    char *error_name = NULL;
    (*jvmti)->GetErrorName (jvmti, error, &error_name);
    write_status ("failed: %s: %s (%d)", operation, error_name == NULL ? "unknown JVMTI error" : error_name, error);
    if (error_name != NULL)
        (*jvmti)->Deallocate (jvmti, (unsigned char *) error_name);
    return JNI_ERR;
}


static unsigned char *read_class_file (const char *path, jint *length)
{
    FILE *file = fopen (path, "rb");
    if (file == NULL)
        return NULL;

    if (fseek (file, 0, SEEK_END) != 0)
    {
        fclose (file);
        return NULL;
    }

    const long size = ftell (file);
    if (size <= 0 || size > INT_MAX || fseek (file, 0, SEEK_SET) != 0)
    {
        fclose (file);
        return NULL;
    }

    unsigned char *bytes = malloc ((size_t) size);
    if (bytes == NULL || fread (bytes, 1, (size_t) size, file) != (size_t) size)
    {
        free (bytes);
        fclose (file);
        return NULL;
    }

    fclose (file);
    *length = (jint) size;
    return bytes;
}


static jclass find_class (jvmtiEnv *jvmti, JNIEnv *jni, const jint class_count, const jclass *classes, const char *signature, jobject controller_loader, jint *matches)
{
    jclass result = NULL;
    *matches = 0;

    for (jint index = 0; index < class_count; index++)
    {
        char *loaded_signature = NULL;
        if ((*jvmti)->GetClassSignature (jvmti, classes[index], &loaded_signature, NULL) != JVMTI_ERROR_NONE)
            continue;

        if (strcmp (loaded_signature, signature) == 0)
        {
            jobject loader = NULL;
            if ((*jvmti)->GetClassLoader (jvmti, classes[index], &loader) == JVMTI_ERROR_NONE && (controller_loader == NULL || (*jni)->IsSameObject (jni, loader, controller_loader)))
            {
                result = classes[index];
                (*matches)++;
            }
            if (loader != NULL)
                (*jni)->DeleteLocalRef (jni, loader);
        }

        (*jvmti)->Deallocate (jvmti, (unsigned char *) loaded_signature);
    }

    return result;
}


JNIEXPORT jint JNICALL Agent_OnAttach (JavaVM *vm, char *options, void *reserved)
{
    (void) reserved;

    if (options == NULL)
    {
        write_status ("failed: missing CLASS_DIRECTORY::CLASS_NAME[,CLASS_NAME...] options");
        return JNI_ERR;
    }

    jvmtiEnv *jvmti = NULL;
    JNIEnv *jni = NULL;
    if ((*vm)->GetEnv (vm, (void **) &jvmti, JVMTI_VERSION_1_2) != JNI_OK || jvmti == NULL || (*vm)->GetEnv (vm, (void **) &jni, JNI_VERSION_1_6) != JNI_OK || jni == NULL)
    {
        write_status ("failed: could not access JVMTI/JNI environments");
        return JNI_ERR;
    }

    jvmtiCapabilities capabilities;
    memset (&capabilities, 0, sizeof (capabilities));
    capabilities.can_redefine_classes = 1;
    jvmtiError error = (*jvmti)->AddCapabilities (jvmti, &capabilities);
    if (error != JVMTI_ERROR_NONE)
        return fail (jvmti, error, "AddCapabilities");

    char *arguments = strdup (options);
    char *separator = arguments == NULL ? NULL : strstr (arguments, "::");
    if (separator == NULL)
    {
        free (arguments);
        write_status ("failed: expected CLASS_DIRECTORY::CLASS_NAME[,CLASS_NAME...]");
        return JNI_ERR;
    }

    *separator = '\0';
    const char *class_directory = arguments;
    char *class_names = separator + 2;
    const char *status_names = strdup (class_names);

    jint class_count = 0;
    jclass *classes = NULL;
    error = (*jvmti)->GetLoadedClasses (jvmti, &class_count, &classes);
    if (error != JVMTI_ERROR_NONE)
    {
        free ((void *) status_names);
        free (arguments);
        return fail (jvmti, error, "GetLoadedClasses");
    }

    jint setup_matches = 0;
    const jclass setup_class = find_class (jvmti, jni, class_count, classes, SETUP_SIGNATURE, NULL, &setup_matches);
    if (setup_matches != 1)
    {
        write_status ("failed: expected one loaded PushControllerSetup, found %d", setup_matches);
        (*jvmti)->Deallocate (jvmti, (unsigned char *) classes);
        free ((void *) status_names);
        free (arguments);
        return JNI_ERR;
    }

    jobject controller_loader = NULL;
    error = (*jvmti)->GetClassLoader (jvmti, setup_class, &controller_loader);
    if (error != JVMTI_ERROR_NONE)
    {
        (*jvmti)->Deallocate (jvmti, (unsigned char *) classes);
        free ((void *) status_names);
        free (arguments);
        return fail (jvmti, error, "GetClassLoader");
    }

    char *state = NULL;
    char *class_name = strtok_r (class_names, ",", &state);
    while (class_name != NULL)
    {
        char internal_name[PATH_MAX];
        size_t class_name_length = strlen (class_name);
        if (class_name_length + 1 > sizeof (internal_name))
        {
            write_status ("failed: class name is too long: %s", class_name);
            error = JVMTI_ERROR_ILLEGAL_ARGUMENT;
            break;
        }

        memcpy (internal_name, class_name, class_name_length + 1);
        for (size_t index = 0; index < class_name_length; index++)
            if (internal_name[index] == '.')
                internal_name[index] = '/';

        char signature[PATH_MAX];
        char class_path[PATH_MAX];
        if (snprintf (signature, sizeof (signature), "L%s;", internal_name) >= (int) sizeof (signature) || snprintf (class_path, sizeof (class_path), "%s/%s.class", class_directory, internal_name) >= (int) sizeof (class_path))
        {
            write_status ("failed: class path is too long: %s", class_name);
            error = JVMTI_ERROR_ILLEGAL_ARGUMENT;
            break;
        }

        jint matches = 0;
        const jclass loaded_class = find_class (jvmti, jni, class_count, classes, signature, controller_loader, &matches);
        if (matches != 1)
        {
            write_status ("failed: expected one active loaded class %s, found %d", class_name, matches);
            error = JVMTI_ERROR_NOT_FOUND;
            break;
        }

        jint byte_count = 0;
        unsigned char *class_bytes = read_class_file (class_path, &byte_count);
        if (class_bytes == NULL)
        {
            write_status ("failed: could not read %s", class_path);
            error = JVMTI_ERROR_NOT_FOUND;
            break;
        }

        const jvmtiClassDefinition definition = {loaded_class, byte_count, class_bytes};
        error = (*jvmti)->RedefineClasses (jvmti, 1, &definition);
        free (class_bytes);
        if (error != JVMTI_ERROR_NONE)
        {
            fail (jvmti, error, class_name);
            break;
        }

        class_name = strtok_r (NULL, ",", &state);
    }

    if (controller_loader != NULL)
        (*jni)->DeleteLocalRef (jni, controller_loader);
    (*jvmti)->Deallocate (jvmti, (unsigned char *) classes);

    if (error == JVMTI_ERROR_NONE)
        write_status ("redefined %s", status_names == NULL ? "requested classes" : status_names);

    free ((void *) status_names);
    free (arguments);
    return error == JVMTI_ERROR_NONE ? JNI_OK : JNI_ERR;
}
