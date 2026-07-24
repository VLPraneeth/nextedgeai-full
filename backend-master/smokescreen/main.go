// This serves as the entry point to the project, initiating the Stripe Smokescreen proxy.
// Typically, this file should remain unchanged, except if there's a need to implement specific proxy authentication.

package main

import (
	"log"
	"os"

	"github.com/stripe/smokescreen/cmd"
	"github.com/stripe/smokescreen/pkg/smokescreen"
)

func main() {
	conf, err := cmd.NewConfiguration(nil, nil)
	if err != nil {
		log.Fatal(err)
	}
	if conf == nil {
		os.Exit(1)
	}
	
	smokescreen.StartWithConfig(conf, nil)
}