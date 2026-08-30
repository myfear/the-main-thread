const statuses = {
    "parcel.accepted": "PICKED_UP",
    "parcel.in_transit": "IN_TRANSIT",
    "parcel.delivered": "DELIVERED",
    "parcel.exception": "EXCEPTION"
};

function normalize(payload) {
    const source = JSON.parse(payload);
    const status = statuses[source.event];

    if (!source.event_id || !source.parcel || !source.parcel.tracking || !source.occurred_at || !status) {
        throw new Error("ParcelBird payload is missing a required field");
    }

    return JSON.stringify({
        carrier: "parcelbird",
        eventId: source.event_id,
        trackingNumber: source.parcel.tracking,
        status: status,
        occurredAt: source.occurred_at
    });
}

export { normalize };
