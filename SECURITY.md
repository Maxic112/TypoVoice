# Security policy

## Supported versions
Only the latest release receives security fixes.

## Reporting a vulnerability
Please do not open a public issue. Report it privately via GitHub:
**Security → Report a vulnerability** in this repository. Include steps to reproduce, the app version and
the impact you expect. You will get a reply as soon as possible.

## Scope notes
- TypoVoice sends recorded audio only to the servers you configure; API keys are stored in the app's private
  storage on the device.
- The accessibility service is used to find the focused text field and insert the recognized text; it does
  not collect or transmit screen content.
