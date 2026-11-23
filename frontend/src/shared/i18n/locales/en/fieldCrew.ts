// Ứng dụng hiện trường của tài xế và hộ tống
export const fieldCrew = {
  myTrips: {
    "run": {
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
  fieldCrew: {
    tabJob: 'Tasks',
    tabJourney: 'Journey',
    tabManifest: 'Manifest',
    noTrips: 'You have no trips requiring action.',
    journeyMilestones: 'Journey Milestones',
    driverVehicleCheck: 'Vehicle inspected: oil, tires, trailer AC, auxiliary generator',
    escortKitTitle: 'Prepare Escort Medical Kit',
    arrivePickup: 'Confirm Arrival at Pickup',
    arriveCheckpoint: 'Confirm Arrival at Checkpoint',
    arriveHint: 'Take on-site photo for dispatch system recording',
    takePhoto: 'On-site Photo',
    escortScanMicrochip: 'Escort Scans Microchip',
    scannedCount: 'Scanned {{scanned}}/{{total}} horses.',
    unfitHorseAlert: 'Excluded {{count}} horses due to illness/fever ({{horses}}). Customer invoiced amount adjusted.',
    collectOriginalDocs: 'Collect Original Documents',
    collectOriginalDocsHint: 'Inspect and collect physical original documents from sender. Check off each document.',
    startTrip: 'Start Journey',
    finishTrip: 'Complete Handover',
    reportSos: 'Report Emergency (SOS)',
  },
} as const
