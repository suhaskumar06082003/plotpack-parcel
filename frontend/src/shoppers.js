// Random but believable customers and items for the simulation.

const FIRST = ["Lakshmi", "Priya", "Divya", "Meena", "Anitha", "Kavya", "Revathi", "Sowmya", "Arjun", "Karthik",
  "Vignesh", "Suresh", "Harini", "Deepa", "Nandini", "Sanjay", "Pooja", "Gayathri", "Bhavana", "Rahul"];
const LAST = ["Iyer", "Reddy", "Nair", "Rao", "Krishnan", "Gowda", "Menon", "Pillai", "Shetty", "Naidu", "Sharma"];

export const ITEMS = [
  { name: "Kanchipuram silk saree", min: 8999, max: 45999 },
  { name: "Soft silk saree", min: 3499, max: 12999 },
  { name: "Cotton saree", min: 899, max: 3499 },
  { name: "Pattu pavadai (kids)", min: 1499, max: 5999 },
  { name: "Silk dhoti set", min: 1299, max: 4999 },
  { name: "Men's kurta", min: 799, max: 2999 },
  { name: "Bridal lehenga", min: 14999, max: 59999 },
  { name: "Salwar set", min: 1199, max: 4999 },
  { name: "Silk dupatta", min: 699, max: 2999 },
];

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const between = (a, b) => a + Math.floor(Math.random() * (b - a + 1));

export const randomCustomer = () => ({
  name: `${pick(FIRST)} ${pick(LAST)}`,
  phone: `9${between(100000000, 999999999)}`,
});

export const randomItem = () => {
  const item = pick(ITEMS);
  return { description: item.name, amount: Math.floor(between(item.min, item.max) / 100) * 100 + 99 };
};

export const randomFloor = () => between(1, 3);
export const basketSize = () => pick([1, 1, 1, 2, 2, 2, 3, 3, 4, 5]);
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
