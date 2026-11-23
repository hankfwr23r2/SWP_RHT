// Ứng dụng hiện trường của tài xế và hộ tống
export const fieldCrew = {
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
