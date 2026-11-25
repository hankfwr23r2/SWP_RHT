// Ứng dụng hiện trường của tài xế và hộ tống
export const fieldCrew = {
  myTrips: {
    "run": {
      "stayIn": {
        "title": "Stable overnight",
        "hint": "The horses on board are left with a partner stable overnight. In the morning, collect them from the stable before leaving.",
        "partner": "Stable",
        "choose": "Choose a stable"
      },
      "papers": {
        "record": "Photo of the signed record",
        "upload": "Upload record",
        "recordSaved": "Record saved",
        "recordAttached": "Record attached",
        "needRecord": "Photograph and upload the signed record before completing.",
        "originals": "Original papers",
        "receive": "Original received",
        "return": "Original returned",
        "RECEIVED": "Original received",
        "RETURNED": "Original returned"
      },
      "stall": "Stall {{n}}",
      "agentParty": "Agent: {{name}}",
      "unpaidReturn": {
        "action": "Return horses: balance overdue",
        "title": "Return the received horses?",
        "message": "The customer has not paid the balance after the waiting time. Every horse received at this stop is handed back (to the shipper, or to the agent at a border gate) and the booking is cancelled. If a VNPAY payment is still pending, the system refuses and you wait a little longer. Confirm that all originals held by the company are returned with the horses.",
        "done": "Horses returned and booking cancelled"
      },
      "refusal": {
        "refuse": "Refuse",
        "title": "Refuse {{name}}?",
        "message": "This horse is removed from the booking, its seat on the departure is released and the quotation is recalculated for the remaining horses. The manager is notified. The other horses are received as usual.",
        "reason": "Reason for refusing",
        "done": "Horse refused"
      },
      "delivery": {
        "startWaiting": "Start waiting for recipient",
        "waitingSince": "Waiting for the recipient since {{time}}",
        "noRecipient": "Recipient not found",
        "noRecipientTitle": "Record that the recipient was not found?",
        "noRecipientMessage": "The horse is not delivered, the delivery leg is marked failed and the coordinator is told to redeliver. Allowed only after the free waiting time.",
        "waitStarted": "Started waiting for recipient",
        "failed": "Recipient not found recorded"
      },
      "incident": {
        "report": "Report incident",
        "reportTitle": "Report an incident",
        "cause": "Cause",
        "scope": "Affects",
        "wholeTrip": "Whole vehicle",
        "incidentTitle": "Title",
        "incidentDescription": "Description",
        "send": "Send report",
        "reported": "Incident reported",
        "confirmFit": "Fit to continue",
        "fitDone": "Fit to continue confirmed",
        "addCost": "Record cost",
        "costDescription": "Item",
        "amount": "Amount (VND)",
        "consulted": "Customer was consulted",
        "costSaved": "Cost recorded"
      },
      "earlyReason": "Reason for leaving early (if rest is not complete)",
      "health": {
        "title": "Log health at this stop",
        "status": "Health",
        "water": "Water",
        "feed": "Feed",
        "temperature": "Temperature (°C)",
        "save": "Save",
        "logged": "Logged",
        "notLogged": "Not logged",
        "saved": "Health logged",
        "statusValue": {
          "NORMAL": "Normal",
          "WATCH": "Watch",
          "SICK": "Sick",
          "CRITICAL": "Critical"
        },
        "intakeValue": {
          "NORMAL": "Normal",
          "REDUCED": "Reduced",
          "REFUSED": "Refused"
        }
      },
      "open": "Open trip",
      "back": "My trips",
      "loadError": "Could not load the trip",
      "start": "Start trip",
      "complete": "Finish trip",
      "stops": "Stops",
      "stopState": {
        "waiting": "Not yet",
        "here": "At this stop",
        "left": "Left"
      },
      "arrive": "Arrived",
      "depart": "Leave",
      "handover": {
        "PICKUP": "Receive horses",
        "DELIVERY": "Deliver horses",
        "HUB_IN": "Hand into hub",
        "RETURN": "Return to shipper",
        "AGENT": "Hand over with agent",
        "SHIFT": "Take over shift",
        "STAY_IN": "Stable overnight",
        "STAY_OUT": "Collect from stable",
        "HUB_OUT": "Take from hub"
      },
      "handoverStatus": {
        "PENDING": "In progress",
        "DONE": "Done",
        "TIMEOUT": "Wait timed out",
        "FAILED": "Failed"
      },
      "chip": "Chip of {{name}}",
      "scan": "Scan",
      "matched": "Chip matches",
      "mismatched": "Wrong chip",
      "notScanned": "Not scanned",
      "completeHandover": "Complete handover",
      "done": {
        "started": "Trip started",
        "arrived": "Arrived at stop",
        "departed": "Left the stop",
        "opened": "Handover opened",
        "scanned": "Chip scanned",
        "handed": "Handover completed",
        "completed": "Trip finished"
      }
    },
    "title": "My trips",
    "description": "Trips you are assigned to. Confirm each one so the coordinator knows you are ready.",
    "loadError": "Could not load your trips",
    "emptyTitle": "No trips yet",
    "emptyDescription": "Trips appear here once the coordinator assigns you.",
    "acknowledge": "Confirm assignment",
    "acknowledged": "Confirmed",
    "waiting": "Waiting for your confirmation",
    "journey": "{{from}} → {{to}}",
    "vehicle": "Vehicle {{plate}}",
    "start": "Starts {{time}}",
    "done": "Assignment confirmed"
  },
} as const
