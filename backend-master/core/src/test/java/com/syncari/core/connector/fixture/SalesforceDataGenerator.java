package com.syncari.core.connector.fixture;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SalesforceDataGenerator {

//    @Test
    public void createContactFile() throws Exception {
        int totalRecords = 10000;

        File file = new File("contact.csv");
        FileWriter outputfile = new FileWriter(file);
        CSVPrinter writer = new CSVPrinter(outputfile, CSVFormat.DEFAULT);
        List<String> randomlyNames = getRandomlyNames();
        List<String> emailDomain = getEmailDomain();
        List<String> companyNames = getCompanyNames();
        List<String> titles = Arrays.asList("CFO", "VP, Finance", "CEO", "SVP, Operations", "SVP, Technology",
                "Manager", "Sales Engineer", "SVP, Administration and Finance", "VP, Facilities", "Director", "");

        // adding header to csv
        writer.printRecord("FirstName", "LastName", "Email", "Title", "AccountName", "Phone");

        for (int i = 0; i < totalRecords; i++) {
            String first = randomlyNames.get(RandomUtils.nextInt(0, randomlyNames.size()));
            String last = randomlyNames.get(RandomUtils.nextInt(0, randomlyNames.size()));
            String domain = emailDomain.get(RandomUtils.nextInt(0, emailDomain.size()));
            String company = companyNames.get(RandomUtils.nextInt(0, companyNames.size()));
            writer.printRecord(first, last, last + "@" + domain,
                    titles.get(RandomUtils.nextInt(0, titles.size())), company, getPhone());
            if (i % 100 == 0) {
                writer.flush();
            }
        }
        writer.close();
        outputfile.close();
    }

    private String getPhone() {
        return String.format("(%s%s%s) %s%s%s-%s%s%s%s", RandomUtils.nextInt(0, 10), RandomUtils.nextInt(0, 10),
                RandomUtils.nextInt(0, 10), RandomUtils.nextInt(0, 10), RandomUtils.nextInt(0, 10),
                RandomUtils.nextInt(0, 10), RandomUtils.nextInt(0, 10), RandomUtils.nextInt(0, 10),
                RandomUtils.nextInt(0, 10), RandomUtils.nextInt(0, 10));
    }

    private List<String> getRandomlyNames() {
        return Arrays.asList("Liam", "Emma", "Noah", "Olivia", "William", "Ava", "James", "Isabella", "Oliver",
                "Sophia", "Benjamin", "Charlotte", "Elijah", "Mia", "Lucas", "Amelia", "Mason", "Harper", "Logan",
                "Evelyn", "Alexander", "Abigail", "Ethan", "Emily", "Jacob", "Elizabeth", "Michael", "Mila", "Daniel",
                "Ella", "Henry", "Avery", "Jackson", "Sofia", "Sebastian", "Camila", "Aiden", "Aria", "Matthew",
                "Scarlett", "Samuel", "Victoria", "David", "Madison", "Joseph", "Luna", "Carter", "Grace", "Owen",
                "Chloe", "Wyatt", "Penelope", "John", "Layla", "Jack", "Riley", "Luke", "Zoey", "Jayden", "Nora",
                "Dylan", "Lily", "Grayson", "Eleanor", "Levi", "Hannah", "Isaac", "Lillian", "Gabriel", "Addison",
                "Julian", "Aubrey", "Mateo", "Ellie", "Anthony", "Stella", "Jaxon", "Natalie", "Lincoln", "Zoe",
                "Joshua", "Leah", "Christopher", "Hazel", "Andrew", "Violet", "Theodore", "Aurora", "Caleb", "Savannah",
                "Ryan", "Audrey", "Asher", "Brooklyn", "Nathan", "Bella", "Thomas", "Claire", "Leo", "Skylar", "Isaiah",
                "Lucy", "Charles", "Paisley", "Josiah", "Everly", "Hudson", "Anna", "Christian", "Caroline", "Hunter",
                "Nova", "Connor", "Genesis", "Eli", "Emilia", "Ezra", "Kennedy", "Aaron", "Samantha", "Landon", "Maya",
                "Adrian", "Willow", "Jonathan", "Kinsley", "Nolan", "Naomi", "Jeremiah", "Aaliyah", "Easton", "Elena",
                "Elias", "Sarah", "Colton", "Ariana", "Cameron", "Allison", "Carson", "Gabriella", "Robert", "Alice",
                "Angel", "Madelyn", "Maverick", "Cora", "Nicholas", "Ruby", "Dominic", "Eva", "Jaxson", "Serenity",
                "Greyson", "Autumn", "Adam", "Adeline", "Ian", "Hailey", "Austin", "Gianna", "Santiago", "Valentina",
                "Jordan", "Isla", "Cooper", "Eliana", "Brayden", "Quinn", "Roman", "Nevaeh", "Evan", "Ivy", "Ezekiel",
                "Sadie", "Xavier", "Piper", "Jose", "Lydia", "Jace", "Alexa", "Jameson", "Josephine", "Leonardo",
                "Emery", "Bryson", "Julia", "Axel", "Delilah", "Everett", "Arianna", "Parker", "Vivian", "Kayden",
                "Kaylee", "Miles", "Sophie", "Sawyer", "Brielle", "Jason", "Madeline");
    }

    private List<String> getEmailDomain() {
        return Arrays.asList("aol.com", "att.net", "comcast.net", "facebook.com", "gmail.com", "gmx.com",
                "googlemail.com", "google.com", "hotmail.com", "hotmail.co.uk", "mac.com", "me.com", "mail.com",
                "msn.com", "live.com", "sbcglobal.net", "verizon.net", "yahoo.com", "yahoo.co.uk", "email.com",
                "fastmail.fm", "games.com", "gmx.net", "hush.com", "hushmail.com", "icloud.com", "iname.com",
                "inbox.com", "lavabit.com", "outlook.com", "pobox.com", "protonmail.ch", "protonmail.com",
                "tutanota.de", "tutanota.com", "tutamail.com", "tuta.io", "keemail.me", "rocketmail.com",
                "safe-mail.net", "wow.com", "ygm.com", "ymail.com", "zoho.com", "yandex.com", "bellsouth.net",
                "charter.net", "cox.net", "earthlink.net", "juno.com", "btinternet.com", "virginmedia.com",
                "blueyonder.co.uk", "freeserve.co.uk", "live.co.uk", "ntlworld.com", "o2.co.uk", "orange.net",
                "sky.com", "talktalk.co.uk", "tiscali.co.uk", "virgin.net", "wanadoo.co.uk", "bt.com", "yahoo.ca",
                "hotmail.ca", "bell.net", "shaw.ca", "sympatico.ca", "rogers.com");
    }

    private List<String> getCompanyNames() {
        return Arrays.asList("Burlington Textiles Corp of America", "Dickenson plc", "GenePoint", "Edge Communications",
                "Grand Hotels & Resorts Ltd", "Express Logistics and Transport", "Pyramid Construction Inc.", "sForce",
                "United Oil & Gas Corp.", "Oracle", "Ibm", "Marketo", "Zendesk", "Gusto", "Facebook");
    }

}
