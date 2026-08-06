// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

#include <CoreFoundation/CoreFoundation.h>
#include <CoreMIDI/CoreMIDI.h>
#include <ctype.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_MIDI_MESSAGE_SIZE 65536
#define MAX_SEND_SIZE 4096

typedef struct
{
    Byte sysex[MAX_MIDI_MESSAGE_SIZE];
    size_t sysex_length;
    Byte short_message[3];
    size_t short_length;
    size_t short_expected;
    Byte running_status;
    int in_sysex;
} MidiParser;

static MidiParser parser;
static pthread_mutex_t parser_mutex = PTHREAD_MUTEX_INITIALIZER;

static void print_bytes(const Byte *bytes, size_t length)
{
    for (size_t index = 0; index < length; index++)
        printf("%s%02X", index == 0 ? "" : " ", bytes[index]);
}

static void emit_message(const Byte *bytes, size_t length)
{
    flockfile(stdout);
    printf("MIDI\t");
    print_bytes(bytes, length);
    printf("\n");
    fflush(stdout);
    funlockfile(stdout);
}

static size_t midi_message_length(Byte status)
{
    if (status < 0xF0)
    {
        const Byte kind = status & 0xF0;
        return kind == 0xC0 || kind == 0xD0 ? 2 : 3;
    }

    switch (status)
    {
        case 0xF1:
        case 0xF3:
            return 2;
        case 0xF2:
            return 3;
        default:
            return 1;
    }
}

static void start_short_message(Byte status)
{
    parser.short_message[0] = status;
    parser.short_length = 1;
    parser.short_expected = midi_message_length(status);
    parser.running_status = status < 0xF0 ? status : 0;
    if (parser.short_expected == 1)
    {
        emit_message(parser.short_message, 1);
        parser.short_length = 0;
    }
}

static void process_midi_byte(Byte value)
{
    if (value >= 0xF8)
    {
        if (value != 0xFE)
            emit_message(&value, 1);
        return;
    }

    if (parser.in_sysex)
    {
        if (value == 0xF0)
            parser.sysex_length = 0;
        if (parser.sysex_length >= sizeof(parser.sysex))
        {
            parser.in_sysex = 0;
            parser.sysex_length = 0;
            return;
        }
        parser.sysex[parser.sysex_length++] = value;
        if (value == 0xF7)
        {
            emit_message(parser.sysex, parser.sysex_length);
            parser.in_sysex = 0;
            parser.sysex_length = 0;
        }
        return;
    }

    if (value == 0xF0)
    {
        parser.in_sysex = 1;
        parser.sysex_length = 1;
        parser.sysex[0] = value;
        parser.short_length = 0;
        parser.running_status = 0;
        return;
    }

    if ((value & 0x80) != 0)
    {
        if (value != 0xF7)
            start_short_message(value);
        return;
    }

    if (parser.short_length == 0)
    {
        if (parser.running_status == 0)
            return;
        parser.short_message[0] = parser.running_status;
        parser.short_length = 1;
        parser.short_expected = midi_message_length(parser.running_status);
    }

    parser.short_message[parser.short_length++] = value;
    if (parser.short_length == parser.short_expected)
    {
        emit_message(parser.short_message, parser.short_length);
        parser.short_length = 0;
    }
}

static void read_midi(const MIDIPacketList *packet_list, void *context, void *connection)
{
    (void)context;
    (void)connection;

    pthread_mutex_lock(&parser_mutex);
    const MIDIPacket *packet = &packet_list->packet[0];
    for (UInt32 packet_index = 0; packet_index < packet_list->numPackets; packet_index++)
    {
        for (UInt16 byte_index = 0; byte_index < packet->length; byte_index++)
            process_midi_byte(packet->data[byte_index]);
        packet = MIDIPacketNext(packet);
    }
    pthread_mutex_unlock(&parser_mutex);
}

static int endpoint_name(MIDIEndpointRef endpoint, char *buffer, size_t size)
{
    CFStringRef name = NULL;
    if (MIDIObjectGetStringProperty(endpoint, kMIDIPropertyDisplayName, &name) != noErr || name == NULL)
        return 0;
    const Boolean copied = CFStringGetCString(name, buffer, (CFIndex)size, kCFStringEncodingUTF8);
    CFRelease(name);
    return copied;
}

static int contains_case_insensitive(const char *text, const char *needle)
{
    const size_t text_length = strlen(text);
    const size_t needle_length = strlen(needle);
    if (needle_length == 0 || needle_length > text_length)
        return 0;

    for (size_t start = 0; start + needle_length <= text_length; start++)
    {
        size_t offset = 0;
        while (offset < needle_length &&
               tolower((unsigned char)text[start + offset]) == tolower((unsigned char)needle[offset]))
            offset++;
        if (offset == needle_length)
            return 1;
    }
    return 0;
}

static MIDIEndpointRef find_source(const char *selector, char *resolved_name, size_t resolved_name_size)
{
    char name[512];
    MIDIEndpointRef match = 0;
    for (ItemCount index = 0; index < MIDIGetNumberOfSources(); index++)
    {
        const MIDIEndpointRef endpoint = MIDIGetSource(index);
        if (!endpoint_name(endpoint, name, sizeof(name)) || !contains_case_insensitive(name, selector))
            continue;
        if (match != 0)
            return 0;
        match = endpoint;
        snprintf(resolved_name, resolved_name_size, "%s", name);
    }
    return match;
}

static MIDIEndpointRef find_destination(const char *selector, char *resolved_name, size_t resolved_name_size)
{
    char name[512];
    MIDIEndpointRef match = 0;
    for (ItemCount index = 0; index < MIDIGetNumberOfDestinations(); index++)
    {
        const MIDIEndpointRef endpoint = MIDIGetDestination(index);
        if (!endpoint_name(endpoint, name, sizeof(name)) || !contains_case_insensitive(name, selector))
            continue;
        if (match != 0)
            return 0;
        match = endpoint;
        snprintf(resolved_name, resolved_name_size, "%s", name);
    }
    return match;
}

static int parse_hex_message(char *line, Byte *bytes, size_t *length)
{
    char *cursor = line;
    *length = 0;
    while (*cursor != '\0')
    {
        while (isspace((unsigned char)*cursor))
            cursor++;
        if (*cursor == '\0')
            break;
        if (*length >= MAX_SEND_SIZE)
            return 0;

        char *end = NULL;
        const unsigned long value = strtoul(cursor, &end, 16);
        if (end == cursor || value > 0xFF || (*end != '\0' && !isspace((unsigned char)*end)))
            return 0;
        bytes[(*length)++] = (Byte)value;
        cursor = end;
    }
    return *length > 0;
}

static int send_message(MIDIPortRef output_port, MIDIEndpointRef destination, const Byte *bytes, size_t length)
{
    Byte packet_storage[MAX_SEND_SIZE + 256];
    MIDIPacketList *packet_list = (MIDIPacketList *)packet_storage;
    MIDIPacket *packet = MIDIPacketListInit(packet_list);
    if (MIDIPacketListAdd(packet_list, sizeof(packet_storage), packet, 0, length, bytes) == NULL)
        return 0;
    return MIDISend(output_port, destination, packet_list) == noErr;
}

int main(int argc, char **argv)
{
    /*
     * The Java parent owns recovery. Keep this transport alive when Ctrl-C or
     * SIGTERM reaches the foreground process group so the parent's shutdown
     * hook can restore the temporary palette through the existing pipes.
     * QUIT, pipe EOF, or a final SIGKILL still terminates the bridge.
     */
    if (signal(SIGINT, SIG_IGN) == SIG_ERR || signal(SIGTERM, SIG_IGN) == SIG_ERR)
    {
        fprintf(stderr, "Could not configure bridge signal handling.\n");
        return 1;
    }

    if (argc != 3)
    {
        fprintf(stderr, "Usage: Push2CoreMIDIBridge INPUT_SELECTOR OUTPUT_SELECTOR\n");
        return 2;
    }

    MIDIClientRef client = 0;
    MIDIPortRef input_port = 0;
    MIDIPortRef output_port = 0;
    char input_name[512] = "";
    char output_name[512] = "";

    if (MIDIClientCreate(CFSTR("DrivenByMoss Push 2 Color Test"), NULL, NULL, &client) != noErr)
    {
        printf("ERROR\tCould not create CoreMIDI client.\n");
        return 1;
    }

    const MIDIEndpointRef source = find_source(argv[1], input_name, sizeof(input_name));
    const MIDIEndpointRef destination = find_destination(argv[2], output_name, sizeof(output_name));
    if (source == 0 || destination == 0)
    {
        printf("ERROR\tCould not resolve unique CoreMIDI input/output endpoints.\n");
        MIDIClientDispose(client);
        return 1;
    }

    if (MIDIInputPortCreate(client, CFSTR("Push 2 Color Test Input"), read_midi, NULL, &input_port) != noErr ||
        MIDIPortConnectSource(input_port, source, NULL) != noErr ||
        MIDIOutputPortCreate(client, CFSTR("Push 2 Color Test Output"), &output_port) != noErr)
    {
        printf("ERROR\tCould not open the selected CoreMIDI endpoints.\n");
        MIDIClientDispose(client);
        return 1;
    }

    printf("READY\t%s\t%s\n", input_name, output_name);
    fflush(stdout);

    char line[MAX_SEND_SIZE * 3 + 32];
    Byte bytes[MAX_SEND_SIZE];
    while (fgets(line, sizeof(line), stdin) != NULL)
    {
        if (strncmp(line, "QUIT", 4) == 0)
            break;
        if (strncmp(line, "SEND\t", 5) != 0)
        {
            printf("ERROR\tUnknown bridge command.\n");
            fflush(stdout);
            continue;
        }

        size_t length = 0;
        if (!parse_hex_message(line + 5, bytes, &length))
        {
            printf("ERROR\tInvalid outbound MIDI message.\n");
            fflush(stdout);
            continue;
        }
        if (!send_message(output_port, destination, bytes, length))
        {
            printf("ERROR\tCoreMIDI failed to send an outbound message.\n");
            fflush(stdout);
        }
    }

    MIDIPortDisconnectSource(input_port, source);
    MIDIPortDispose(input_port);
    MIDIPortDispose(output_port);
    MIDIClientDispose(client);
    return 0;
}
